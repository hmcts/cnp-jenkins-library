import uk.gov.hmcts.contino.slack.SlackChannelRetriever
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.contino.MetricsPublisher

/**
 * Send build failure notification
 * <p>
 * When build happens on master branch then specified team channel is notified. In other cases change author is notified instead.
 * <p>
 * When change author does not have Slack account or uses other Slack username than one registered in LDAP then notification won't be sent.
 *
 * @param args arguments:
 *  <ul>
 *      <li>channel - (string; required) name of the slack channel for team notifications</li>
 *  </ul>
 */
def call(String teamSlackChannel, MetricsPublisher metricsPublisher ) {
  String slackWarningMessage = ""
  String warningMessage = WarningCollector.getSlackWarningMessage()
  String changeAuthor = env.CHANGE_AUTHOR

  publishWarningMetrics(metricsPublisher)

  if(!warningMessage.trim().isEmpty()){

    echo warningMessage

    String channel
    if (! new ProjectBranch(env.BRANCH_NAME).isMaster()) {
      channel = new SlackChannelRetriever(this).retrieve(teamSlackChannel, changeAuthor)
      if(channel == null ){
        slackWarningMessage = slackWarningMessage.concat("@channel , this is sent here as ${changeAuthor} github user doesn't have a slack mapping in https://github.com/hmcts/github-slack-user-mappings \n\n ")
      }
    }
    if(channel == null ) {
      channel = teamSlackChannel
    }
    slackWarningMessage = slackWarningMessage.concat("We have noticed deprecated configuration in ${env.JOB_NAME}: <${env.RUN_DISPLAY_URL}|Build ${env.BUILD_DISPLAY_NAME}> \n\n ")
      .concat(warningMessage)

    slackSend(
      failOnError: true,
      channel: channel,
      color: 'warning',
      message: slackWarningMessage)
  }

}

void publishWarningMetrics(MetricsPublisher metricsPublisher) {
  for (pipelineWarning in WarningCollector.pipelineWarnings) {
    metricsPublisher.publish(pipelineWarning.warningKey)
  }

}
