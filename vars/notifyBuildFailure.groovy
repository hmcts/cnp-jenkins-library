import uk.gov.hmcts.contino.slack.SlackChannelRetriever
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
def call(Map args = [:]) {
  validate(args)

  String changeAuthor = env.CHANGE_AUTHOR

  String channel
  if (env.BRANCH_NAME == 'master') {
    channel = args.channel
  } else {
    channel = new SlackChannelRetriever(this).retrieve(args.channel as String, changeAuthor)
  }

  echo 'using channel: ' + channel
  echo 'change author: ' + changeAuthor

  try {
    slackSend(
      channel: channel,
      color: 'danger',
      message: "${env.JOB_NAME}: <${env.RUN_DISPLAY_URL}|Build ${env.BUILD_DISPLAY_NAME}> has FAILED")
  } catch (Exception ex) {
    echo "ERROR: Failed to notify ${channel} due to the following error: ${ex}"
  }
}

private static validate(Map args) {
  if (args.channel == null) throw new Exception('Slack channel is required')
}
