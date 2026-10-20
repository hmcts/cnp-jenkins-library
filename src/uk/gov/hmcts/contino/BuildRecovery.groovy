package uk.gov.hmcts.contino

import com.cloudbees.groovy.cps.NonCPS
import jenkins.model.Jenkins

class BuildRecovery implements Serializable {
  static final String TEST_ARTIFACTS = '**/build/classes/**,**/build/resources/**,**/build/test-results/**,**/build/reports/jacoco/**,**/build/jacoco/**'
  private final def steps
  private final def currentBuild
  private final def publisher
  private final boolean enabled
  private Set<String> reusable = [] as Set
  String sourceBuild

  BuildRecovery(steps, currentBuild, publisher, boolean enabled = true) {
    this.steps = steps
    this.currentBuild = currentBuild
    this.publisher = publisher
    this.enabled = enabled
  }

  void prepare(boolean canRestoreTests) {
    reusable.clear()
    sourceBuild = null
    if (!enabled || currentBuild.previousBuild?.result != 'FAILURE') {
      return
    }
    Map identity = publisher.recoveryIdentity()
    if (!identity.values().every { it }) {
      steps.echo 'Build recovery: identity is incomplete; running all steps'
      return
    }
    sourceBuild = currentBuild.previousBuild.number.toString()
    try {
      reusable = reusableSteps(readMetrics(identity, sourceBuild), identity, sourceBuild)
      if (reusable.contains('test')) {
        if (!canRestoreTests) {
          reusable.remove('test')
        } else {
          try {
            steps.copyArtifacts(projectName: identity.job_name, selector: steps.specific(sourceBuild), filter: TEST_ARTIFACTS, optional: false)
            steps.junit(testResults: '**/test-results/test/*.xml', allowEmptyResults: false)
          } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
              throw failure
            }
            reusable.remove('test')
            steps.echo 'Build recovery: test artifacts could not be restored; rerunning tests'
          }
        }
      }
      steps.echo "Build recovery from #${sourceBuild}: ${reusable ? reusable.join(', ') : 'no steps eligible'}"
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) {
        throw failure
      }
      reusable.clear()
      steps.echo "Build recovery unavailable (${failure.class.simpleName}); running all steps"
    }
  }

  boolean shouldSkip(String stage) {
    return enabled && reusable.contains(stage)
  }

  void recordSkip(String stage) {
    steps.echo "Skipping ${stage}: successful in build #${sourceBuild} for the same commit"
    publisher.publish(stage, [event_type: 'step', step_result: 'SKIPPED', reused_from_build: sourceBuild])
  }

  void archiveTestArtifacts() {
    if (!enabled) {
      return
    }
    try {
      steps.archiveArtifacts(artifacts: TEST_ARTIFACTS, allowEmptyArchive: false)
      publisher.publish('test', [event_type: 'step_artifacts', artifact_paths: TEST_ARTIFACTS])
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) {
        throw failure
      }
      steps.echo 'Build recovery: test artifacts could not be archived; tests will not be reusable'
    }
  }

  @NonCPS
  static Set<String> reusableSteps(List<Map> records, Map identity, String buildNumber) {
    if (!records || records.size() > 1000 || records.any { it.attempt_id } || !identity.values().every { it }) {
      return [] as Set
    }
    if (!records.every { record -> record.build_number?.toString() == buildNumber && identity.every { key, value -> record[key] == value } }) {
      return [] as Set
    }
    def terminal = records.findAll { it.event_type == 'pipeline' && it.pipeline_result == 'FAILURE' }
    if (terminal.size() != 1) {
      return [] as Set
    }
    def outcomes = records.findAll { it.event_type == 'step' }
    def failures = outcomes.findAll { it.step_result == 'FAILURE' }
    if (!failures.every { it.failure_reason == 'AGENT_DISCONNECTED' } ||
        !(terminal[0].failure_reason == 'AGENT_DISCONNECTED' || failures)) {
      return [] as Set
    }
    Set<String> eligible = [] as Set
    ['test', 'securitychecks'].each { stage ->
      def events = outcomes.findAll { it.current_step_name == stage }
      def executionIds = events.collect { it.step_execution_id }.unique()
      if (executionIds.size() == 1 && executionIds[0] && events.size() == 2 &&
          events.count { it.step_result == 'STARTED' } == 1 && events.count { it.step_result == 'SUCCESS' } == 1) {
        eligible.add(stage)
      }
    }
    if (!records.any { it.event_type == 'step_artifacts' && it.current_step_name == 'test' && it.artifact_paths == TEST_ARTIFACTS }) {
      eligible.remove('test')
    }
    return eligible
  }

  @NonCPS
  protected List<Map> readMetrics(Map identity, String buildNumber) {
    def loader = Jenkins.get().pluginManager.uberClassLoader
    def cache = loader.loadClass('io.jenkins.plugins.azurecosmosdb.AzureCosmosDBCache')
    def client = cache.get('cosmos-connection', currentBuild.rawBuild.parent)
    def sdkLoader = cache.classLoader
    def parameterClass = sdkLoader.loadClass('com.azure.cosmos.models.SqlParameter')
    def parameters = []
    ['product', 'component', 'branch_name', 'job_name'].each { key ->
      parameters.add(parameterClass.newInstance("@${key}".toString(), identity[key]))
    }
    parameters.add(parameterClass.newInstance('@build_number', buildNumber))
    String queryText = 'SELECT TOP 1001 * FROM c WHERE c.product = @product AND c.component = @component AND c.branch_name = @branch_name AND c.job_name = @job_name AND c.build_number = @build_number'
    def query = sdkLoader.loadClass('com.azure.cosmos.models.SqlQuerySpec').newInstance(queryText, parameters)
    def options = sdkLoader.loadClass('com.azure.cosmos.models.CosmosQueryRequestOptions').newInstance()
    String database = steps.env.PIPELINE_METRICS_DATABASE?.toString()?.trim() ?: CosmosDbTargetResolver.DEFAULT_DATABASE
    def container = client.getDatabase(database).getContainer('pipeline-metrics')
    List<Map> records = []
    def iterator = container.queryItems(query, options, Map.class).iterator()
    while (iterator.hasNext() && records.size() <= 1000) {
      records.add(new LinkedHashMap(iterator.next()))
    }
    return records
  }
}