package uk.gov.hmcts.contino

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MetricsPublisher implements Serializable {

  def steps
  def env
  def currentBuild
  def cosmosDbUrl
  def resourceLink
  def product
  def component

  MetricsPublisher(steps, currentBuild, product, component) {
    this.product = product
    this.component = component
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.cosmosDbUrl = env.COSMOSDB_URL ?: 'https://pipeline-metrics.documents.azure.com/'
    this.resourceLink = env.COSMOSDB_METRICS_RESOURCE_LINK ?: 'dbs/jenkins/colls/pipeline-metrics'
  }

  @NonCPS
  private def collectMetrics(currentStepName) {
    def dateBuildScheduled = new Date(currentBuild.timeInMillis as long)

    return [
      id                           : "${UUID.randomUUID().toString()}",
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
      current_build_absolute_url   : currentBuild.absoluteUrl
    ]
  }

  @NonCPS
  private def generateAuthToken(verb, resourceType, formattedDate, tokenType, tokenVersion, tokenKey) {
    def stringToSign = verb.toLowerCase() + "\n" + resourceType.toLowerCase() + "\n" + resourceLink + "\n" + formattedDate.toLowerCase() + "\n" + "" + "\n"
    def decodedKey = tokenKey.decodeBase64()
    def hash = hmacSHA256(decodedKey, stringToSign)
    def base64Hash = Base64.getEncoder().encodeToString(hash)

    def authToken = "type=${tokenType}&ver=${tokenVersion}&sig=${base64Hash}"
    return URLEncoder.encode(authToken, 'UTF-8')
  }

  @NonCPS
  private def hmacSHA256(secretKey, data) {
    Mac mac = Mac.getInstance('HmacSHA256')
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, 'HmacSHA256')
    mac.init(secretKeySpec)
    byte[] digest = mac.doFinal(data.getBytes())
    return digest
  }

  def publish(currentStepName) {
    try {
      steps.withCredentials([[$class: 'StringBinding', credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY']]) {
        if (env.COSMOSDB_TOKEN_KEY == null) {
          steps.echo "Set the 'COSMOSDB_TOKEN_KEY' environment variable to enable metrics publishing"
          return
        }

        def metrics = collectMetrics(currentStepName)
        def data = JsonOutput.toJson(metrics).toString()
        steps.echo "Publishing Metrics data"

        def verb = 'POST'
        def resourceType = "docs"
        def formattedDate = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").format(ZonedDateTime.now(ZoneOffset.UTC)) // must stay on 1 line to avoid serialisation
        def tokenType = env.COSMOSDB_TOKEN_TYPE ?: 'master'
        def tokenVersion = env.COSMOSDB_TOKEN_VERSION ?: '1.0'
        def tokenKey = env.COSMOSDB_TOKEN_KEY

        def authHeaderValue = generateAuthToken(verb, resourceType, formattedDate, tokenType, tokenVersion, tokenKey)
        //steps.echo "Auth Header: " + authHeaderValue

        steps.httpRequest httpMode: "${verb}",
          requestBody: "${data}",
          contentType: 'APPLICATION_JSON',
          quiet: true,
          consoleLogResponseBody: false,
          url: "${cosmosDbUrl}${resourceLink}/${resourceType}",
          customHeaders: [
            [name: 'Authorization', value: "${authHeaderValue}"],
            [name: 'x-ms-version', value: '2017-02-22'],
            [name: 'x-ms-date', value: "${formattedDate}"],
          ]
      }
    } catch (err) {
      steps.echo "Unable to log metrics '${err}'"
    }
  }
}
