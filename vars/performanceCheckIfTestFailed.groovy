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

  //Get recent commits
  def commits = sh(
    script: "git log -n 1 --pretty=format:'%an, %ad, %s' --date=format:'%Y-%m-%d %H:%M:%S'",
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
      if ((previousBuild.result == 'FAILURE')) {
        resultList.add('Fail')
      } else {
        resultList.add('Pass')
      }
      previousBuild = previousBuild?.previousBuild
      loopCount++
    }

    //Set colour for slack message
    def colour = (constantFailure >= threshold2) ? "danger" : "warning"
    def matches = ("${env.JOB_NAME}" =~ /(.*)/)
    def testName
    testName = matches.find() ? matches.group(1) : "no name"

    //Build the body text
    def body =
"""
---------------------------------------------
*ALERT:* ${testName}
* Build URL: ${env.BUILD_URL}
---------------------------------------------
This test has failed ${constantFailure-1} times in a row since ${buildDate[-2]}
---------------------------------------------
The below list shows the last ${previousRunsLimit} runs:
* New -> Old ${resultList[0..previousRunsLimit - 1]}
---------------------------------------------
Last commit on this repo:
* ${commits}
---------------------------------------------
"""

    //Send slack message to channel or user
    if (constantFailure >= threshold1)
      sendSlackMessage("${user}", colour, "${body}")
  }
}





