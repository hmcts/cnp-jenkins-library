//performanceCheckIfTestFailed.groovy
/*
 * Checks if last Performance Gatling test failed several times times in a row
 * and sends a slack message to a user or channel
 *
 * performanceCheckIfTestFailed('slack user id')
 */

def call(String user) {

  def previousRunsLimit = 10
  def threshold1 = 3
  def threshold2 = 5

  if (currentBuild.result == 'FAILURE') {
    def loopCount = 1
    def failureCount = 1
    def previousBuild = currentBuild
    def resultList = ['Fail']
    while (loopCount < previousRunsLimit + 1) {
      if ((previousBuild.result == 'FAILURE')) {
        resultList.add('Fail')
        failureCount++
      } else {
        resultList.add('Pass')
      }
      previousBuild = previousBuild?.previousBuild
      if (previousBuild == null) {
        previousRunsLimit = loopCount
        break
      }
      loopCount++
    }

    def buildDate
    def lastCommitDate
    def constantFailure = 1
    previousBuild = currentBuild
    while (previousBuild == "FAILURE") {
      previousBuild = previousBuild?.previousBuild
      if (previousBuild != null) {
        buildDate = new Date("${previousBuild.getTimeInMillis()}".toLong()).format("yyyy-MM-dd HH:mm:ss")
        def lastEntry = prev.changeSets[-1]?.items?.last()
        if (lastEntry)
          lastCommitDate = new Date("${lastEntry.timestamp}".toLong()).format("yyyy-MM-dd HH:mm:ss")
        constantFailure++
      }
    }

    def colour = (constantFailure <= threshold2) ? "danger" : "warning"
    def commitMessage

    if (constantFailure > threshold1) {
      commitMessage = (buildDate > lastCommitDate) ? "There has been no commit to the repo since the date this test started failing."
        : "There was a commit on ${lastCommitDate} and test is still failing."
      def body =
        """-----------------------------
        *ALERT*
        ${env.JOB_NAME}
        -----------------------------
        This test has failed ${constantFailure} times in a row since ${buildDate}
        -----------------------------
        The below list shows the last ${previousRunsLimit} runs.
        ${resultList}
        -----------------------------
        ${commitMessage}
        -----------------------------
        Last test details:
        > *Job Name:* ${env.JOB_NAME}
        > *Build Number:* ${env.BUILD_NUMBER}
        > *Build URL:* ${env.BUILD_URL}
        -----------------------------"""

      slackMessage("${user}", colour, "${body}")
    }
  }
}




