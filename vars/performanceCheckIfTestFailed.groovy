//performanceCheckIfTestFailed.groovy
/*
 * Checks if last Performance Gatling test failed several times times in a row
 * and sends a slack message to a user or channel
 *
 * performanceCheckIfTestFailed('slack user id')
 */

def call(String user) {

  if (currentBuild.result == 'FAILURE') {

    def final threshold_warning = 3
    def final threshold_danger = 6
    def final previousRunsLimit = 5

    //Get recent commits
    def commits = sh(
      script: "git log -n 1 --pretty=format:'%an | %ad | %s' --date=format:'%Y-%m-%d %H:%M:%S'",
      returnStdout: true
    ).trim()

    //Create fail & pass list to display
    //Count consecutive failures in a row
    def resultList = []
    def buildDate = null
    def consecutiveFailureStop = false
    def consecutiveFailureCount = 0
    def previousBuild = currentBuild
    while (previousBuild != null) {
      if ((previousBuild.result == 'FAILURE')) {
        resultList.add('Fail')
        if (consecutiveFailureStop == false) {
          //Count consecutive failures whilst consecutiveFailureStop is false
          consecutiveFailureCount++
          buildDate = (new Date("${previousBuild.getTimeInMillis()}".toLong()).format("yyyy-MM-dd HH:mm:ss"))
        }
      } else {
        resultList.add('Pass')
        //Stop counting consecutive failures when value is set to true
        consecutiveFailureStop = true
      }
      previousBuild = previousBuild?.previousBuild
    }

    //Set colour for slack message
    def colour = (consecutiveFailureCount >= threshold_danger) ? "danger" : "warning"

    //Extract actual test name from job name
    def testName = "${env.JOB_NAME}".split("\\/")

    //Build the body text
    def body =
      """
---------------------------------------------
*ALERT:* ${testName[1]} (<${env.BUILD_URL}|Build ${env.BUILD_NUMBER}>)
---------------------------------------------
This test has failed ${consecutiveFailureCount} times in a row since ${buildDate}. Last ${previousRunsLimit} runs:
 >New -> Old ${resultList[0..previousRunsLimit - 1]}
---------------------------------------------
Last commit on this repo:
 >${commits}
---------------------------------------------
"""

    //Send slack message to channel or user
    if (consecutiveFailureCount >= threshold_warning)
      sendSlackMessage("${user}", colour, "${body}")

  }
}












/*


def call(String user) {

  def threshold = 3
  def threshold2 = 6
  def previousBuild = currentBuild

  //Get recent commits
  def commits = sh(
    script: "git log -n 1 --pretty=format:'%an | %ad | %s' --date=format:'%Y-%m-%d %H:%M:%S'",
    returnStdout: true
  ).trim()

  //Check how many runs this repo has
  def previousRunsLimit = 0
  while (previousBuild != null) {
    previousRunsLimit++
    previousBuild = previousBuild?.previousBuild
  }

  //Find number of fails in a row
  def buildDate = []
  def id = []
  def constantFailure = 0
  if (currentBuild.result == 'FAILURE') {
    previousBuild = currentBuild
    while (previousBuild.result == "FAILURE") {
      constantFailure++
      buildDate.add(new Date("${previousBuild.getTimeInMillis()}".toLong()).format("yyyy-MM-dd HH:mm:ss"))
      id.add(previousBuild.id)
      previousBuild = previousBuild?.previousBuild
    }

    //Create fail list
    def loopCount = 0
    def resultList = []
    if (previousRunsLimit >= 5)
      previousRunsLimit = 5
    previousBuild = currentBuild
    while ((loopCount < previousRunsLimit) && (previousBuild != null)) {
      if ((previousBuild.result == 'FAILURE'))
        resultList.add('Fail')
      else
        resultList.add('Pass')
      previousBuild = previousBuild?.previousBuild
      loopCount++
    }

    //Set colour for slack message
    def colour = (constantFailure >= threshold2) ? "danger" : "warning"

    //Extract actual test name from job name
    def testName = "${env.JOB_NAME}".split("\\/")

    //Build the body text
    def body =
"""
---------------------------------------------
*ALERT:* ${testName[1]} (<${env.BUILD_URL}|Build ${env.BUILD_NUMBER}>)
---------------------------------------------
This test has failed ${constantFailure} times in a row since ${buildDate[-1]}. Last ${previousRunsLimit} runs:
 >New -> Old ${resultList[0..previousRunsLimit - 1]}
---------------------------------------------
Last commit on this repo:
 >${commits}
---------------------------------------------
"""

    //Send slack message to channel or user
    if (constantFailure >= threshold1)
      sendSlackMessage("${user}", colour, "${body}")

  }
}

*/



