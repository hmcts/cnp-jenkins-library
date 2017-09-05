package uk.gov.hmcts.contino

class BuildHelper implements Serializable {

  def steps
  def gitUrl

  BuildHelper(steps){
    this.steps = steps
    this.gitUrl = "${steps.GITHUB_PROTOCOL}://${steps.TOKEN}@${steps.GITHUB_REPO}"
  }

  def nextTagVersion() {
    //Fetch all tags. They are not available due to shallow checkout in first step
    steps.sh(script: "git fetch '${gitUrl}' --tags", returnStdout: true).split("\r?\n")

    /* TODO: uncomment when GIT version on Jenkins is updated and has --sort option
             would be most reliable solution to get last tag
    def lines = sh(script: 'git tag --list --sort="version:refname" -n0', returnStdout: true).split("\r?\n")
    println lines*/

    //meanwhile read tags this way:
    String lastTagVersion = steps.sh(script: 'git describe --tags $(git rev-list --tags --max-count=1)', returnStdout: true)
    println "Last tag version in repo: " + lastTagVersion
    String[] lastTagSplitted = lastTagVersion.split(/\./)
    lastTagSplitted[lastTagSplitted.length - 1] = lastTagSplitted[lastTagSplitted.length - 1].toInteger() + 1

    return lastTagSplitted.join('.')
  }

  def tag(tag) {
    String result = ""
    println("Step variable: "+ steps)
    if (steps.env.BRANCH_NAME == 'master' &&
        steps.currentBuild.currentResult == 'SUCCESS')
    {
      result = "Will tag with version: " + tag
      steps.sh("git tag -a $tag -m \"Jenkins\"")
      steps.sh("git push '${gitUrl}' --tags")
    } else
      result = "Not on 'master' branch! Next succesfull build on master will be tagged with: " + tag
    return result
  }
}
