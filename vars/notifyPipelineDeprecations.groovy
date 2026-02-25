import uk.gov.hmcts.contino.slack.SlackChannelRetriever
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.SlackBlockMessage

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
def call(teamSlackChannel, metricsPublisher ) {
  def warningMessage = WarningCollector.getSlackWarningMessage()
  // Fetch all block sections from the warnings mesage to see if anything has been added
  String warnings = warningMessage.blocks.collect { it.text.text }.join("\n\n")

  String changeAuthor = env.CHANGE_AUTHOR

  publishWarningMetrics(metricsPublisher)

  // Only send if there are blocks in the warning message meaning there is something to send
  if (!warnings.isEmpty()) {
    String channel
    if (! new ProjectBranch(env.BRANCH_NAME).isMaster()) {
      channel = new SlackChannelRetriever(this).retrieve(teamSlackChannel, changeAuthor)
      if(channel == null ){
        warningMessage.addSection("@channel , this is sent here as ${changeAuthor} github user doesn't have a slack mapping in https://github.com/hmcts/github-slack-user-mappings")
      }
    }
    if(channel == null ) {
      channel = teamSlackChannel
    }
    if (channel == "@iamabotuser") {
       echo "Skipping notification on PRs from bot user"
       return
    }

    // Build our slack message for pipeline deprecations
    warningMessage.setWarningColor()
    warningMessage.addFirstHeader("We have noticed the following deprecated configuration:")
    warningMessage.addSection("In ${env?.JOB_NAME}: <${env?.RUN_DISPLAY_URL}|Build ${env?.BUILD_DISPLAY_NAME}>")

    try {
      // slackSend(
        // failOnError: true,
        // channel: channel,
        // attachments: warningMessage.asObject())
    }
    catch (Exception ex) {
      if(channel!='@iamabotuser') {
        throw new Exception("ERROR: Failed to notify ${channel} due to the following error: ${ex}")
      }
    }
  }
}

static void publishWarningMetrics(metricsPublisher) {
  for (pipelineWarning in WarningCollector.pipelineWarnings) {
    metricsPublisher.publish(pipelineWarning.warningKey)
  }
}
