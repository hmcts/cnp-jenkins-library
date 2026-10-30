package uk.gov.hmcts.contino

import com.cloudbees.groovy.cps.NonCPS
import jenkins.model.Jenkins

class BuildRecovery implements Serializable {
  static final String TEST_ARTIFACTS = '**/build/classes/**,**/build/resources/**,**/build/test-results/**,**/build/reports/jacoco/**,**/build/jacoco/**'
  static final List<String> STATIC_CHECK_STEPS = ['test', 'sonarscan', 'securitychecks', 'techstack', 'dockerbuild', 'dockertestbuild', 'fortify-scan']
  private final def steps
  private final def currentBuild
  private final def publisher
  private final boolean enabled
  private Set<String> reusable = [] as Set
  private boolean checked = false
  private String lastSuccessfulStage
  String sourceBuild

  BuildRecovery(steps, currentBuild, publisher, boolean enabled = true) {
    this.steps = steps
    this.currentBuild = currentBuild
    this.publisher = publisher
    this.enabled = enabled
  }

  void checkPreviousBuild() {
    checked = true
    reusable.clear()
    sourceBuild = null
    lastSuccessfulStage = null
    if (!enabled) {
      steps.echo 'Build recovery: disabled; Cosmos DB not queried; running all steps'
      return
    }
    if (currentBuild.previousBuild == null) {
      steps.echo 'Build recovery: no previous build; Cosmos DB not queried; running all steps'
      return
    }
    if (currentBuild.previousBuild.result != 'FAILURE') {
      steps.echo "Build recovery: previous build #${currentBuild.previousBuild.number} result is ${currentBuild.previousBuild.result ?: 'not available'}; Cosmos DB not queried; running all steps"
      return
    }
    Map identity = publisher.recoveryIdentity()
    if (!identity.values().every { it }) {
      steps.echo 'Build recovery: identity is incomplete; running all steps'
      return
    }
    sourceBuild = currentBuild.previousBuild.number.toString()
    try {
      steps.echo "Build recovery: querying Cosmos DB pipeline-metrics for job ${identity.job_name}, product ${identity.product}, component ${identity.component}, branch ${identity.branch_name}, build #${sourceBuild}"
      List<Map> records = readMetrics(identity, sourceBuild)
      steps.echo "Build recovery: Cosmos DB returned ${records?.size() ?: 0} records for build #${sourceBuild}"
      if (records != null && records.size() <= 1000) {
        records.findAll { it.event_type == 'step' && it.step_result != 'STARTED' && it.failure_reason == 'AGENT_DISCONNECTED' }.each { record ->
          steps.echo "Previous build #${sourceBuild}: ${record.current_step_name}=${record.step_result}, node_status=${record.node_status ?: 'not recorded'}, failure_reason=${record.failure_reason ?: 'none'}"
        }
      }
      reusable = reusableSteps(records, identity, sourceBuild)
      def terminal = records?.find { it.event_type == 'pipeline' && it.pipeline_result == 'FAILURE' }
      if (terminal) {
        lastSuccessfulStage = terminal.last_successful_stage
        steps.echo "Build recovery: previous build #${sourceBuild} last successful stage was ${terminal.last_successful_stage ?: 'not recorded'}"
      }
      String rejection = rejectionReason(records, identity, sourceBuild)
      steps.echo "Build recovery from #${sourceBuild}: ${reusable ? reusable.join(', ') : "no steps eligible; ${rejection ?: 'no completed steps that can be reused'}"}"
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) {
        throw failure
      }
      reusable.clear()
      steps.echo "Build recovery unavailable (${failure.class.simpleName}); running all steps"
    }
  }

  void prepare(boolean canRestoreTests, boolean imageExists = false) {
    if (!checked) {
      checkPreviousBuild()
    }
    if (reusable.contains('dockerbuild') && !imageExists) {
      reusable.remove('dockerbuild')
      steps.echo 'Build recovery: image for this commit is not in ACR; rebuilding Docker image'
    }
    // Skipping the whole group also skips the Docker build inside it.
    if (reusable.contains('static checks') && !imageExists) {
      reusable.remove('static checks')
      steps.echo 'Build recovery: image for this commit is not in ACR; running static checks'
    }
    if (reusable.contains('test')) {
      if (!canRestoreTests) {
        reusable.remove('test')
        steps.echo 'Build recovery: unit tests will run; artifact restoration is only supported for Gradle builds'
      } else {
        try {
          Map identity = publisher.recoveryIdentity()
          steps.echo "Build recovery: restoring unit-test artifacts from build #${sourceBuild}"
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
    if (sourceBuild) {
      steps.echo "Build recovery ready from #${sourceBuild}: ${reusable ? "skipping ${reusable.join(', ')}" : 'no steps will be skipped'}"
      String resumePoint = getResumeAfter()
      if (resumePoint) {
        steps.echo "Build recovery: resuming after ${resumePoint}"
      }
    }
  }

  // The last completed stage that this build will actually skip.
  String getResumeAfter() {
    if (lastSuccessfulStage && reusable.contains(lastSuccessfulStage)) {
      return lastSuccessfulStage
    }
    String promotion = reusable.find { it.endsWith(':promotion') }
    return promotion ?: (reusable.contains('static checks') ? 'static checks' : null)
  }

  boolean shouldSkip(String stage) {
    return enabled && reusable.contains(stage)
  }

  void recordSkip(String stage) {
    steps.echo "Skipping ${stage}: successful in build #${sourceBuild} for the same commit"
    publisher.lastSuccessfulStage = stage
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
    if (rejectionReason(records, identity, buildNumber) != null) {
      return [] as Set
    }
    def outcomes = records.findAll { it.event_type == 'step' }
    Set<String> eligible = [] as Set
    def succeeded = { String stage ->
      def events = outcomes.findAll { it.current_step_name == stage }
      def executionIds = events.collect { it.step_execution_id }.unique()
      executionIds.size() == 1 && executionIds[0] && events.size() == 2 &&
        events.count { it.step_result == 'STARTED' } == 1 && events.count { it.step_result == 'SUCCESS' } == 1 &&
        events.every { it.git_commit == identity.git_commit && it.git_url == identity.git_url }
    }
    ['test', 'securitychecks', 'static checks'].each { stage ->
      if (succeeded(stage)) {
        eligible.add(stage)
      }
    }
    // Builds recorded before the group record existed only have per-branch records.
    def startedChecks = STATIC_CHECK_STEPS.findAll { step -> outcomes.any { it.current_step_name == step } }
    if (startedChecks.containsAll(['test', 'securitychecks']) && startedChecks.every { succeeded(it) }) {
      eligible.add('static checks')
    }
    if (!records.any { it.event_type == 'step_artifacts' && it.current_step_name == 'test' && it.artifact_paths == TEST_ARTIFACTS }) {
      eligible.remove('test')
    }
    // The stage record also covers agent switching and workspace stash-back around the promotion.
    outcomes.collect { it.current_step_name?.toString() }.unique().findAll { it?.endsWith(':promotion') }.each { String step ->
      String label = step - ':promotion'
      if (succeeded(step) && succeeded("${label} build promotion".toString())) {
        eligible.add(step)
      }
    }
    if (eligible.any { it.endsWith(':promotion') }) {
      eligible.add('dockerbuild')
    }
    return eligible
  }

  @NonCPS
  static String rejectionReason(List<Map> records, Map identity, String buildNumber) {
    if (!identity.values().every { it }) {
      return 'this build is missing identity details'
    }
    if (!records) {
      return "no metrics records found for build #${buildNumber}"
    }
    if (records.size() > 1000) {
      return 'too many metrics records to evaluate'
    }
    if (records.any { it.attempt_id }) {
      return 'records use the retired retry-attempt format'
    }
    // Records published before checkout have no Git details yet.
    def mismatched = identity.keySet().find { key ->
      records.any { record -> !(record[key] == identity[key] || (record[key] == null && key in ['git_url', 'git_commit'])) }
    }
    if (records.any { it.build_number?.toString() != buildNumber }) {
      mismatched = 'build_number'
    }
    if (mismatched) {
      return "previous build ${mismatched} does not match this build"
    }
    def terminal = records.findAll { it.event_type == 'pipeline' && it.pipeline_result == 'FAILURE' }
    if (terminal.size() != 1) {
      return "expected one pipeline FAILURE record, found ${terminal.size()}"
    }
    if (terminal[0].git_commit != identity.git_commit) {
      return 'previous pipeline record does not have the same Git commit'
    }
    def failures = records.findAll { it.event_type == 'step' && it.step_result == 'FAILURE' }
    def otherFailure = failures.find { it.failure_reason != 'AGENT_DISCONNECTED' }
    if (otherFailure) {
      return "step ${otherFailure.current_step_name} failed with ${otherFailure.failure_reason ?: 'no reason'}"
    }
    if (!(terminal[0].failure_reason == 'AGENT_DISCONNECTED' || failures)) {
      return "previous pipeline failed with ${terminal[0].failure_reason ?: 'no reason'}, not AGENT_DISCONNECTED"
    }
    return null
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