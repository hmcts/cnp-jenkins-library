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
  def loopCount = 0
  def failureCount = 0
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
  //previousRunsLimit = previousRunsLimit - 1

  //Find number of fails in a row
  if (currentBuild.result == 'FAILURE') {
    previousBuild = currentBuild
    while (previousBuild.result == "FAILURE") {
      constantFailure++
      buildDate = new Date("${previousBuild.getTimeInMillis()}".toLong()).format("yyyy-MM-dd HH:mm:ss")
      echo constantFailure + " " + buildDate
      if (previousBuild && previousBuild.changeSets) {
        def lastChangeSet = previousBuild.changeSets[-1]
        def lastCommit = lastChangeSet.items?.last()

        if (lastCommit) {
          lastCommitDateMessage =  "${lastCommit.msg}"
          lastCommitDateAuthor = "$lastCommit.author}"
          lastCommitDate = new Date(lastCommit.timestamp).format('yyyy-MM-dd HH:mm:ss')}
          echo lastCommitDate
        }

      previousBuild = previousBuild?.previousBuild
    }

    //Create fail list
    previousBuild = currentBuild
    while ((loopCount < previousRunsLimit) && (previousBuild != null)) {
      if ((previousBuild.result == 'FAILURE')) {
        resultList.add('Fail')
        failureCount++
      } else {
        echo "inside pass"
        resultList.add('Pass')
      }
      echo loopCount + previousBuild.result
      previousBuild = previousBuild?.previousBuild
      loopCount++
    }

    //Set colour for slack message
    def colour = (constantFailure >= threshold2) ? "danger" : "warning"

    //Create slack message body
    if (constantFailure > threshold1) {
      commitMessage = (buildDate > lastCommitDate) ? "There has been no commit to the repo since ${lastCommitDate} the date this test started failing."
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
        Last commit on this repo:
        ${commitMessage}
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




