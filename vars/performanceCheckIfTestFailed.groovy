//performanceCheckIfTestFailed.groovy
/*
 * Checks if last Performance Gatling test failed several times times in a row
 * and sends a slack message to a user or channel
 *
 * performanceCheckIfTestFailed('slack user id')
 */

def call(String user) {

  def threshold1 = 3
  def threshold2 = 5
  def previousBuild = currentBuild
  def previousRunsLimit = 0
  def loopCount = 1
  def failureCount = 1
  def resultList = []
  def constantFailure = 0
  def buildDate
  def commitMessage
  def lastCommitDate

  //Check how many runs this repo has
  while (previousBuild != null) {
    previousRunsLimit++
    previousBuild = previousBuild?.previousBuild
  }
  previousRunsLimit = previousRunsLimit-1

  //Find number of fails in a row
  if (currentBuild.result == 'FAILURE') {
    previousBuild = currentBuild
    while (previousBuild.result == "FAILURE") {
      constantFailure++
      buildDate = new Date("${previousBuild.getTimeInMillis()}".toLong()).format("yyyy-MM-dd HH:mm:ss")
      //def lastEntry = prev.changeSets[-1]?.items?.last()
      //if (lastEntry)
      //  lastCommitDate = new Date("${lastEntry.timestamp}".toLong()).format("yyyy-MM-dd HH:mm:ss")
      //echo "yr: inside if - after lastCommitDate - build date is ${lastCommitDate}"
      previousBuild = previousBuild?.previousBuild
    }

    //Create fail list
    previousBuild = currentBuild
    while (loopCount < previousRunsLimit) {
      if ((previousBuild.result == 'FAILURE')) {
        resultList.add('Fail')
        failureCount++
      } else {
        resultList.add('Pass')
      }
      previousBuild = previousBuild?.previousBuild
      loopCount++
    }

    //Set colour for slack message
    def colour = (constantFailure >= threshold2) ? "danger" : "warning"

    //Create slack message body
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
        Last test details:
        > *Job Name:* ${env.JOB_NAME}
        > *Build Number:* ${env.BUILD_NUMBER}
        > *Build URL:* ${env.BUILD_URL}
        -----------------------------"""

      //Send slack message to channel or user
      sendSlackMessage("${user}", colour, "${body}")
    }
  }
}




