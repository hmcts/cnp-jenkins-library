import uk.gov.hmcts.contino.slack.SlackChannelRetriever
import uk.gov.hmcts.contino.ProjectBranch

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

  String channel
  if (new ProjectBranch(env.BRANCH_NAME).isMaster()) {
    channel = args.channel
  } else {
    channel = new SlackChannelRetriever(this).retrieve(args.channel as String, changeAuthor)
  }

  try {
    if (currentBuild.getPreviousBuild()?.getResult() == 'FAILURE') {
      slackSend(
        channel: channel,
        color: 'good',
        message: "${env.JOB_NAME}: <${env.RUN_DISPLAY_URL}|Build ${env.BUILD_DISPLAY_NAME}> is Fixed")
    }
  } catch (Exception ex) {
    echo "ERROR: Failed to notify ${channel} due to the following error: ${ex}"
  }
}

private static validate(Map args) {
  if (args.channel == null) throw new Exception('Slack channel is required')
}
