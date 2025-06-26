//checkIfGatlingTestFailedThenReport.groovy
/*
 * Checks if last Gatling test failed and sends a slack message to a user or channel
 *
 * checkIfGatlingTestFailedThenReport('slack user id')
 */
def call(String user) {
  def body=
    """-----------------------------
       The following test has failed twice today.
       Please fix within 3 days.
       -----------------------------
       * Job name: ${env.JOB_NAME}
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



