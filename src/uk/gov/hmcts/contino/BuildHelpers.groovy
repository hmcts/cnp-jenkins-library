package uk.gov.hmcts.contino

class BuildHelper extends Serialzable
{

  def steps
  def gitUrl

  BuildHelper(steps, gitUrl){
    this.steps = steps
    this.gitUrl = "${steps.GITHUB_PROTOCOL}://${steps.TOKEN}@${steps.GITHUB_REPO}"
  }

  private void nextTagVersion() {
    //Fetch all tags. They are not available due to shallow checkout in first step
    def fetchTags = steps.sh(script: "git fetch '${gitUrl}' --tags", returnStdout: true).split("\r?\n")

    /* TODO: uncomment when GIT version on Jenkins is updated and has --sort option
             would be most reliable solution to get last tag
    def lines = sh(script: 'git tag --list --sort="version:refname" -n0', returnStdout: true).split("\r?\n")
    println lines*/

    //meanwhile read tags this way:
    def lastTagVersion = steps.sh(script: 'git describe --tags $(git rev-list --tags --max-count=1)', returnStdout: true)
    println "Last tag version in repo: " + lastTagVersion
    def lastTagSplitted = lastTagVersion.split(/\./)
    lastTagSplitted[lastTagSplitted.length - 1] = lastTagSplitted[lastTagSplitted.length - 1].toInteger() + 1
    return lastTagSplitted.join('.')
  }
}
