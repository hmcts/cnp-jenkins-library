package uk.gov.hmcts.contino;
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput;

public class MetricsPublisher implements Serializable {

  private final static String defaultCosmosDbUrl = 'https://build.documents.azure.com'
  def steps
  def env
  def currentBuild
  def cosmosDbUrl
  def buildStartTimeMillis

  MetricsPublisher(steps, currentBuild, cosmosDbUrl) {
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.cosmosDbUrl = cosmosDbUrl
    this.buildStartTimeMillis = currentBuild?.startTimeInMillis
  }

  MetricsPublisher(steps, currentBuild) {
    this(steps, currentBuild, defaultCosmosDbUrl)
  }

  @NonCPS
  private def collectMetrics() {
    return [
      branch_name: env.BRANCH_NAME
//      change_id: env.CHANGE_ID,
//      change_url: env.CHANGE_URL,
//      change_title: env.CHANGE_TITLE,
//      change_author: env.CHANGE_AUTHOR,
//      change_author_display_name: env.CHANGE_AUTHOR_DISPLAY_NAME,
//      change_author_email: env.CHANGE_AUTHOR_EMAIL,
//      change_target: env.CHANGE_TARGET,
//      build_number: env.BUILD_NUMBER,
//      build_id: env.BUILD_ID,
//      build_display_name: env.BUILD_DISPLAY_NAME,
//      job_name: env.JOB_NAME,
//      job_base_name: env.JOB_BASE_NAME,
//      build_tag: env.BUILD_TAG,
//      node_name: env.NODE_NAME,
//      node_labels: env.NODE_LABELS,
//      workspace: env.WORKSPACE,
//      build_url: env.BUILD_URL,
//      job_url: env.JOB_URL,
//      current_build_number: currentBuild.number,
//      current_build_result: currentBuild.result,
//      current_build_current_result: currentBuild.currentResult,
//      current_build_display_name: currentBuild.displayName,
//      current_build_id: currentBuild.id,
//      current_build_time_in_millis: currentBuild.timeInMillis,
//      current_build_duration: currentBuild.duration,
//      current_build_duration_string: currentBuild.durationString,
//      current_build_previous_build: currentBuild.previousBuild.number,
//      current_build_next_build: currentBuild.nextBuild.number,
//      current_build_absolute_url: currentBuild.absoluteUrl
  ]
  }

  @NonCPS
  private def generateCommandString() {
//    def metrics = collectMetrics()
//    steps.echo collectMetrics()
//    def json = JsonOutput.toJson(metrics)
//
//    def data = json.toString()
    return "curl -i -v -XPOST -H 'Content-Type: application/json' --max-time 10 '${cosmosDbUrl}' --data '{branch_name=chris-test}'"
  }

  def publish() {
    def commandString = generateCommandString()
    steps.echo commandString
    try {
      steps.sh script: "${commandString}", returnStdout: true
    } catch (err) {
      steps.echo "Unable to log metrics '${err.message}'"
    }
  }
}
