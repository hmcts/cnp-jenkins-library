// vars/gatlingAlerts.groovy

def slack_message(String user, String colour, String message) {
  slackSend(
    channel: "${user}",
    color: "${colour}",
    message: "${message}"
  )
}

def check_if_test_failed_then_report(String user) {
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
    slack_message("${user}", "warning", "${body}")
  }
}

def check_if_test_failed_intermitently_then_report (String user) {
  def loopMax   = 20
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
    """-----------------------------
            The following test has failed ${failureCount} times in 20 executions.
            Please fix within 3 days.
            -----------------------------
            * Job name: ${env.JOB_NAME}
            -----------------------------"""

  if (failureCount > 5) {
    slack_message("${user}", "warning", "${body}")
  }
}


