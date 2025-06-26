//checkIfGatlingTestFailedThenReport.groovy
/*
 * Checks if last Gatling test failed and sends a slack message to a user or channel
 *
 * checkIfGatlingTestFailedThenReport('slack user id')
 */
def call(String user) {
  def body=
    """-----------------------------
    ${env.JOB_NAME}
    -----------------------------
    This test has failed twice today.
    -----------------------------
    * Build Number: ${env.BUILD_NUMBER}
    * Build URL: ${env.BUILD_URL}
    -----------------------------"""

  if (currentBuild.result == 'FAILURE') {
    slackMessage("${user}", "warning", "${body}")
    return true
  }
  else {
    return false
  }
}



