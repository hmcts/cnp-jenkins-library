package uk.gov.hmcts.contino;
import com.cloudbees.groovy.cps.NonCPS;

public class MetricsPublisher implements Serializable {

  private final static String measurement = 'build_result_data'
  private final static String defaultInfluxDbUrl = 'http://reformmgmtgrafana01.reform.hmcts.net:8086/write?db=jenkins&epoch=ms'
  private final static String separator = ' '
  private final static String keyValueDelimiter = ','
  def steps
  def env
  def currentBuild
  def influxDbUrl
  def team
  def buildStartTimeMillis

  InfluxDbPublisher(steps, currentBuild, team, influxDbUrl) {
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.influxDbUrl = influxDbUrl
    this.team = team
    this.buildStartTimeMillis = currentBuild?.startTimeInMillis
  }

  InfluxDbPublisher(steps, currentBuild, team) {
    this(steps, currentBuild, team, defaultInfluxDbUrl)
  }

  @NonCPS
  private def getBuildTags() {
    return [
    job_name: env.JOB_BASE_NAME,
      branch_name: env.BRANCH_NAME,
      node_name: env.NODE_NAME,
      team: team
  ]
  }

  @NonCPS
  private def getBuildFields() {
    return [
    branch_name: env.BRANCH_NAME,
      change_id: env.CHANGE_ID,
      change_url: env.CHANGE_URL,
      change_title: env.CHANGE_TITLE,
      change_author: env.CHANGE_AUTHOR,
      change_author_display_name: env.CHANGE_AUTHOR_DISPLAY_NAME,
      change_author_email: env.CHANGE_AUTHOR_EMAIL,
      change_target: env.CHANGE_TARGET,
      build_number: env.BUILD_NUMBER,
      build_id: env.BUILD_ID,
      build_display_name: env.BUILD_DISPLAY_NAME,
      job_name: env.JOB_NAME,
      job_base_name: env.JOB_BASE_NAME,
      build_tag: env.BUILD_TAG,
      node_name: env.NODE_NAME,
      node_labels: env.NODE_LABELS,
      workspace: env.WORKSPACE,
      build_url: env.BUILD_URL,
      job_url: env.JOB_URL,
      current_build_number: currentBuild.number,
      current_build_result: currentBuild.result,
      current_build_current_result: currentBuild.currentResult,
      current_build_display_name: currentBuild.displayName,
      current_build_id: currentBuild.id,
      current_build_time_in_millis: currentBuild.timeInMillis,
      current_build_duration: currentBuild.duration,
      current_build_duration_string: currentBuild.durationString,
      current_build_previous_build: currentBuild.previousBuild,
      current_build_next_build: currentBuild.nextBuild,
      current_build_absolute_url: currentBuild.absoluteUrl
  ]
  }

  @NonCPS
  private def writeKeyValueStringFromMap(map, quoteValue) {
    def sbKeyValues = new StringBuilder()
    def prefix = '';
    map.each {
      if (it.value?.toString()?.trim()) {
        if (quoteValue) {
          sbKeyValues << prefix << it.key << '="' << it.value << '"'
        } else {
          sbKeyValues << prefix << it.key << '=' << it.value
        }
        prefix = keyValueDelimiter
      }
    }
    return sbKeyValues?.toString()
  }

  @NonCPS
  private def generateCommandString() {
    def buildTagKeyValueString = writeKeyValueStringFromMap(getBuildTags(), false)
    def buildFieldKeyValueString = writeKeyValueStringFromMap(getBuildFields(), true)

    def sbDataPayload = new StringBuilder()
    sbDataPayload << measurement << keyValueDelimiter << buildTagKeyValueString << separator << buildFieldKeyValueString << separator << buildStartTimeMillis

    def dataPayload = sbDataPayload.toString()
    return "curl -i -v -XPOST --max-time 10 '${influxDbUrl}' --data-binary '${dataPayload}'"
  }

  def publish() {
    def commandString = generateCommandString()
    steps.echo commandString
    steps.sh script: "${commandString}", returnStdout: true
  }
}
