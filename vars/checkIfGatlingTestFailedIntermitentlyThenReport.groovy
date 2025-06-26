//checkIfGatlingTestFailedIntermitentlyThenReport.groovy
/*
 * Checks if last Gatling test failed and sends a slack message to a user or channel
 *
 * checkIfGatlingTestFailedIntermitentlyThenReport('slack user id', 20)
 */
def call (String user, Integer max) {
  def loopMax   = "${max}".toInteger()
  def LoopCount = 1
  def failureCount = 0
  def previousBuild = currentBuild
  while (LoopCount < loopMax+1) {
    if ((previousBuild.result == 'FAILURE')) {
      failureCount = failureCount + 1
    }
    previousBuild = previousBuild.previousBuild
    LoopCount = LoopCount + 1
  }

  def body=
    """  -----------------------------
       ${env.JOB_NAME}
       -----------------------------
       ALERT: The following test has failed ${failureCount} times in ${max} executions.
       -----------------------------"""

  if (failureCount > 5) {
    slackMessage("${user}", "warning", "${body}")
    return true
  }
  else {
    return false
  }
}
