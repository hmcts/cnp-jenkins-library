import uk.gov.hmcts.contino.slack.SlackChannelRetriever
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.pipeline.SlackBlockMessage

/**
 * Send build fixed notification
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
def call(Map args = [:]) {
  validate(args)

  String changeAuthor = env.CHANGE_AUTHOR
  def message = "${env.JOB_NAME}: <${env.RUN_DISPLAY_URL}|Build ${env.BUILD_DISPLAY_NAME}> is Fixed"
  String channel
  if (new ProjectBranch(env.BRANCH_NAME).isMaster()) {
    channel = args.channel
  } else {
    channel = new SlackChannelRetriever(this).retrieve(args.channel as String, changeAuthor)
    if(channel==null) {
      message = "@channel , this is sent here as ${changeAuthor} github user doesn't have a slack mapping in https://github.com/hmcts/github-slack-user-mappings \n\n ".concat(message)
      channel = args.channel
    }
    if (channel == "@iamabotuser") {
       echo "Skipping notification on PRs from bot user"
       return
     }
  }

  try {
    if (currentBuild.getPreviousBuild()?.getResult() == 'FAILURE') {
      // Create block message and add our built message to it as a new section
      def slackMessage = new SlackBlockMessage()
      slackMessage.addSection(message)

      slackSend(
        failOnError: true,
        channel: channel,
        attachments: slackMessage.asObject())
    }
  } catch (Exception ex) {
    if(channel!='@iamabotuser')
      throw new Exception("ERROR: Failed to notify ${channel} due to the following error: ${ex}")
  }
}

private static validate(Map args) {
  if (args.channel == null) throw new Exception('Slack channel is required')
}
