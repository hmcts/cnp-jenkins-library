package uk.gov.hmcts.contino
import org.apache.commons.lang3.RandomStringUtils

class Tagging implements Serializable {

  def pipe
  def gitUrl

  Tagging(pipe){
    this.pipe = pipe
    this.gitUrl = "${pipe.GITHUB_PROTOCOL}://${pipe.TOKEN}@${pipe.GITHUB_REPO}"
  }

  def nextTag() {
    //Fetch all tags. They are not available due to shallow checkout in first step
    pipe.sh(script: "git fetch '${gitUrl}' --tags", returnStdout: true).split("\r?\n")

    /* TODO: uncomment when GIT version on Jenkins is updated and has --sort option
             would be most reliable solution to get last tag
    def lines = sh(script: 'git tag --list --sort="version:refname" -n0', returnStdout: true).split("\r?\n")
    println lines*/

    //meanwhile read tags this way:
    String lastTagVersion = pipe.sh(script: 'git describe --tags $(git rev-list --tags --max-count=1)', returnStdout: true)
    println "Last tag version in repo: " + lastTagVersion
    String[] lastTagSplitted = lastTagVersion.split(/\./)
    lastTagSplitted[lastTagSplitted.length - 1] = lastTagSplitted[lastTagSplitted.length - 1].toInteger() + 1

    return lastTagSplitted.join('.')
  }

  def applyTag(tag) {
    String result = ""
    println("BRANCH_NAME: "+ pipe.env.BRANCH_NAME+ "; currentBuild.currentResult: "+ pipe.currentBuild.currentResult)
    if (pipe.env.BRANCH_NAME == 'master' &&
        pipe.currentBuild.currentResult == 'SUCCESS')
    {
      result = "Tagging with version: " + tag
      pipe.sh("git tag -a $tag -m \"Jenkins\"")
      pipe.sh("git push '${gitUrl}' --tags")
    }
    else
      result = "No tagging done! Available tag is: " + tag

    return result
  }

}
