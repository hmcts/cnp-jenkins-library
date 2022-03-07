package uk.gov.hmcts.contino
@Grab('com.microsoft.azure:azure-documentdb:1.15.2')

import com.cloudbees.groovy.cps.NonCPS
import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonOutput

class MetricsPublisher implements Serializable {

  def steps
  def env
  def currentBuild
  def cosmosDbUrl
  def resourceLink
  def product
  def component
  def correlationId

  MetricsPublisher(steps, currentBuild, product, component, subscription) {
    this.product = product
    this.component = component
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.correlationId = UUID.randomUUID()

    def tmpCosmosDbUrl = this.env.PIPELINE_METRICS_URL
    if (tmpCosmosDbUrl?.trim()) {
      this.cosmosDbUrl = tmpCosmosDbUrl
    } else {
      this.cosmosDbUrl = 'https://pipeline-metrics.documents.azure.com/'
    }
    this.resourceLink = 'dbs/jenkins/colls/pipeline-metrics'
  }

  @NonCPS
  private def collectMetrics(currentStepName) {
    def dateBuildScheduled = new Date(currentBuild.timeInMillis as long)

    return [
      id                           : "${UUID.randomUUID().toString()}",
      correlation_id               : "${correlationId.toString()}",
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
  private def createDocument(metrics, cosmosDbUrl) {
    def client = new DocumentClient(cosmosDbUrl, env.COSMOSDB_TOKEN_KEY, null, null)

    try {
      def data = JsonOutput.toJson(metrics).toString()
      client.createDocument(resourceLink, new Document(data), null, false)
      data = null
    } finally {
      client.close()
      client = null
    }

  }

  def publish(eventName) {
    try {
      steps.withCredentials([[$class: 'StringBinding', credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY']]) {
        if (env.COSMOSDB_TOKEN_KEY == null) {
          steps.echo "Set the 'COSMOSDB_TOKEN_KEY' environment variable to enable metrics publishing"
          return
        }

        steps.echo "Publishing Metrics data to ${cosmosDbUrl}"
        createDocument(collectMetrics(eventName), cosmosDbUrl)
      }
    } catch (err) {
      steps.echo "Unable to log metrics '${err}'"
    }
  }
}
