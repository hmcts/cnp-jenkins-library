package uk.gov.hmcts.contino

import com.cloudbees.groovy.cps.NonCPS
import jenkins.model.Jenkins

class MetricsPublisher implements Serializable {

  def steps
  def env
  def currentBuild
  String product
  String component
  String correlationId
  boolean agentDisconnectionRecorded = false
  String disconnectedAgent
  String lastSuccessfulStage
  CosmosDbTargetResolver cosmosDbTargetResolver

  MetricsPublisher(steps, currentBuild, product, component, CosmosDbTargetResolver cosmosDbTargetResolver = null) {
    this.product = product
    this.component = component
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.correlationId = UUID.randomUUID().toString()
    this.cosmosDbTargetResolver = cosmosDbTargetResolver ?: new CosmosDbTargetResolver(steps)
  }

  private def collectMetrics(currentStepName) {
    def dateBuildScheduled = new Date(currentBuild.timeInMillis as long)
    def (libName, libVersion) = resolveSharedLibrary()

    return [
      id                           : UUID.randomUUID().toString(),
      correlation_id               : correlationId,
      product                      : product,
      component                    : component,
      branch_name                  : env.BRANCH_NAME,
      build_number                 : env.BUILD_NUMBER,
      build_id                     : env.BUILD_ID,
      build_display_name           : env.BUILD_DISPLAY_NAME,
      job_name                     : env.JOB_NAME,
      job_base_name                : env.JOB_BASE_NAME,
      build_tag                    : env.BUILD_TAG,
      node_name                    : env.NODE_NAME,
      node_labels                  : env.NODE_LABELS,
      node_status                  : resolveAgentStatus(),
      build_url                    : env.BUILD_URL,
      job_url                      : env.JOB_URL,
      git_url                      : env.GIT_URL,
      git_commit                   : env.GIT_COMMIT,
      shared_library_name          : libName,
      shared_library_version       : libVersion,
      current_build_number         : currentBuild.number,
      current_step_name            : currentStepName,
      current_build_result         : currentBuild.result,
      current_build_current_result : currentBuild.currentResult,
      current_build_display_name   : currentBuild.displayName,
      current_build_id             : currentBuild.id,
      current_build_scheduled_time : dateBuildScheduled?.format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC")),
      current_build_duration       : currentBuild.duration,
      current_build_duration_string: currentBuild.durationString,
      current_build_previous_build : currentBuild.previousBuild?.number,
      current_build_absolute_url   : currentBuild.absoluteUrl,
      stage_timestamp              : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC")),
    ]
  }

  @NonCPS
  private String resolveAgentStatus() {
    return isAgentOnline(env.NODE_NAME) ? 'online' : 'offline'
  }

  @NonCPS
  boolean isAgentOnline(String nodeName) {
    if (!nodeName) {
      return false
    }
    try {
      def computer = agentComputer(nodeName)
      return computer?.isOnline() && computer?.getChannel() != null
    } catch (Exception ignored) {
      return false
    }
  }

  @NonCPS
  protected def agentComputer(String nodeName) {
    return Jenkins.get().getComputer(nodeName)
  }

  private List<String> resolveSharedLibrary() {    
    // try different library names as it is possible for these to vary. Default is Infrastructure
    def namesToTry = env?.SHARED_LIBRARY_NAME ? [env.SHARED_LIBRARY_NAME] :  ['Infrastructure', 'Pipeline', 'Tagged']

    // Jenkins does not expose a standard env var for shared library version.
    // Try reading loaded library metadata from the build at runtime.
    try {
      def actionClass = this.class.classLoader.loadClass('org.jenkinsci.plugins.workflow.libs.LibrariesAction')
      def action = currentBuild?.rawBuild?.getAction(actionClass)

      // Try each name in order, return first match
      for (name in namesToTry) {
        def record = action?.libraries?.find { it.name == name }
        if (record?.version) {
          return [record.name, record.version]
        }
      }
      
      // If no match found, return first loaded library as fallback
      def firstLib = action?.libraries?.first()
      if (firstLib) {
        return [firstLib.name, firstLib.version]
      }
    } catch (ignored) {
      // silence errors
    }

    // Fallback for pipeline tests
    if (env?.SHARED_LIBRARY_VERSION) {
      return [env?.SHARED_LIBRARY_NAME ?: namesToTry[0], env.SHARED_LIBRARY_VERSION]
    }

      return [null, null]
  }

  Map recoveryIdentity() {
    def (libName, libVersion) = resolveSharedLibrary()
    return [product: product, component: component, branch_name: env.BRANCH_NAME,
      job_name: env.JOB_NAME, git_url: env.GIT_URL, git_commit: env.GIT_COMMIT,
      shared_library_name: libName, shared_library_version: libVersion]
  }

  void publishStep(String stage, String result, String executionId, Throwable failure = null) {
    String reason = failureReason(failure)
    if (reason == 'AGENT_DISCONNECTED') {
      agentDisconnectionRecorded = true
      disconnectedAgent = disconnectedAgent ?: env.NODE_NAME
    }
    if (result == 'SUCCESS') {
      lastSuccessfulStage = stage
    }
    publish(stage, [
      event_type: 'step',
      step_result: result,
      step_execution_id: executionId,
      failure_reason: reason
    ])
  }

  // Cleanup on a lost agent can replace the original exception, so step records take precedence.
  String pipelineFailureReason(Throwable failure, String defaultReason) {
    return agentDisconnectionRecorded || failureReason(failure) == 'AGENT_DISCONNECTED' ? 'AGENT_DISCONNECTED' : defaultReason
  }

  @NonCPS
  private String failureReason(Throwable failure) {
    if (failure == null) {
      return null
    }
    if (isAgentFailure(failure)) {
      return 'AGENT_DISCONNECTED'
    }
    boolean connectionFailure = failure.class.name == 'hudson.AbortException' &&
      ((failure.message ?: '') =~ /(?i)(cannot contact|channel.*(?:closed|closing)|agent.*(?:offline|removed)|script returned exit code (?:-2|137|143)\b)/).find()
    if (connectionFailure && env.NODE_NAME) {
      try {
        def computer = agentComputer(env.NODE_NAME.toString())
        if (computer == null || !computer.isOnline() || computer.getChannel() == null) {
          return 'AGENT_DISCONNECTED'
        }
      } catch (Exception ignored) {
        return 'STEP_FAILURE'
      }
    }
    return 'STEP_FAILURE'
  }

  @NonCPS
  static boolean isAgentFailure(Throwable failure) {
    List<Throwable> pending = [failure]
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>())
    while (!pending.isEmpty()) {
      Throwable current = pending.remove(0)
      if (current == null || !visited.add(current)) {
        continue
      }
      if (current.class.name in [
        'hudson.remoting.ChannelClosedException',
        'hudson.remoting.RequestAbortedException',
        'java.nio.channels.ClosedChannelException',
        'org.jenkinsci.plugins.workflow.support.steps.AgentOfflineException'
      ]) {
        return true
      }
      if (current.class.name == 'org.jenkinsci.plugins.workflow.steps.FlowInterruptedException' &&
          current.causes.any { it.class.simpleName in ['RemovedNodeCause', 'RemovedNodeTimeoutCause'] }) {
        return true
      }
      if (((current.message ?: '') =~ /(?i)(unable to create live FilePath|was marked offline|has been removed or offline|assuming it is not coming back)/).find()) {
        return true
      }
      pending.add(current.cause)
      pending.addAll(current.suppressed as List)
    }
    return false
  }

  def publish(eventName = null, Map details = [:]) {
    try {
      if (details.event_type == 'pipeline') {
        details = details + [last_successful_stage: lastSuccessfulStage]
      }
      def database = cosmosDbTargetResolver.databaseName()
      steps.azureCosmosDBCreateDocument(container: 'pipeline-metrics', credentialsId: 'cosmos-connection', database: database, document: collectMetrics(eventName) + details)
    } catch (err) {
      steps.echo "Unable to log metrics '${err}'"
    }
  }
}
