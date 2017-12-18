package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.StringEscapeUtils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.cloudbees.groovy.cps.NonCPS

class MetricsPublisher implements Serializable {

  private final static String defaultCosmosDbUrl = 'https://e520fc7bdb51bf9c.documents.azure.com:/'
  private final static String defaultCollectionLink = 'dbs/jenkins-sandbox/colls/pipeline-metrics'
  def steps
  def env
  def currentBuild
  def cosmosDbUrl
  def buildStartTimeMillis
  def resourceLink

  MetricsPublisher(steps, currentBuild, cosmosDbUrl, collectionLink) {
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.cosmosDbUrl = cosmosDbUrl
    this.buildStartTimeMillis = currentBuild?.startTimeInMillis
    this.resourceLink = collectionLink
  }

  MetricsPublisher(steps, currentBuild) {
    this(steps, currentBuild, defaultCosmosDbUrl, defaultCollectionLink)
  }

  @NonCPS
  private def collectMetrics(currentStepName) {
    return [
      id                           : "${UUID.randomUUID().toString()}",
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
      current_build_current_result : currentBuild.currentResult,
      current_build_display_name   : currentBuild.displayName,
      current_build_id             : currentBuild.id,
      current_build_time_in_millis : currentBuild.timeInMillis,
      current_build_duration       : currentBuild.duration,
      current_build_duration_string: currentBuild.durationString,
      current_build_previous_build : currentBuild.previousBuild?.number,
      current_build_absolute_url   : currentBuild.absoluteUrl
    ]
  }

  @NonCPS
  private def generateAuthToken(verb, resourceType, formattedDate, tokenType, tokenVersion, tokenKey) {
    def stringToSign = verb.toLowerCase() + "\n" + resourceType.toLowerCase() + "\n" + resourceLink + "\n" + formattedDate.toLowerCase() + "\n" + "" + "\n"
    steps.echo 'Signed payload: ' + StringEscapeUtils.escapeJava(stringToSign)

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
        steps.echo "Request Body: ${data}"

        def verb = 'POST'
        def resourceType = "docs"
        def formattedDate = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.now())
        def tokenType = env.COSMOSDB_TOKEN_TYPE ?: 'master'
        def tokenVersion = env.COSMOSDB_TOKEN_VERSION ?: '1.0'
        def tokenKey = env.COSMOSDB_TOKEN_KEY

        def authHeaderValue = generateAuthToken(verb, resourceType, formattedDate, tokenType, tokenVersion, tokenKey)
        steps.echo "Auth Header: ${authHeaderValue}"

        steps.httpRequest httpMode: "${verb}",
          requestBody: "${data}",
          contentType: 'APPLICATION_JSON',
          consoleLogResponseBody: true,
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
