//slackMessage.groovy
/*
 * Use this to send a specific slack message to a user or channel
 *
 * slackMessage('slack user id', 'warning', 'Hello World')
 */

def call(String user, String colour, String message) {
  if (user == "") or (colour == "") or (message == "") {
    log.info("check parameters - one or more is empty")
  }
  else {
    slackSend(
      channel: "${user}",
      color: "${colour}",
      message: "${message}"
    )
  }
}
