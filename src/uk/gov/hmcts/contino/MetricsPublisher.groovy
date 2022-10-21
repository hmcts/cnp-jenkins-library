package uk.gov.hmcts.contino

class MetricsPublisher implements Serializable {

  def steps
  def env
  def currentBuild
  String product
  String component
  String correlationId

  MetricsPublisher(steps, currentBuild, product, component) {
    this.product = product
    this.component = component
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.correlationId = UUID.randomUUID().toString()
  }

  private def collectMetrics(currentStepName) {
    def dateBuildScheduled = new Date(currentBuild.timeInMillis as long)

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


  def publish(eventName) {
    try {
      steps.azureCosmosDBCreateDocument(container: 'pipeline-metrics', credentialsId: 'cosmos-connection', database: 'jenkins', document: collectMetrics(eventName))
    } catch (err) {
      steps.echo "Unable to log metrics '${err}'"
    }
  }
}
