//sendSlackMessage.groovy
/*
 * Use this to send a specific slack message to a user or channel
 *
 * sendSlackMessage('slack user id', 'warning', 'Hello World')
 * colour key:
 * warning=yellow
 * danger=red
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
