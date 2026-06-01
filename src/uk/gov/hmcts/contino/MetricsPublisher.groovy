package uk.gov.hmcts.contino

class MetricsPublisher implements Serializable {

  def steps
  def env
  def currentBuild
  String product
  String component
  String correlationId
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

      return [null, null]
  }

  def publish(eventName) {
    try {
      def database = cosmosDbTargetResolver.databaseName()
      steps.azureCosmosDBCreateDocument(container: 'pipeline-metrics', credentialsId: 'cosmos-connection', database: database, document: collectMetrics(eventName))
    } catch (err) {
      steps.echo "Unable to log metrics '${err}'"
    }
  }
}
