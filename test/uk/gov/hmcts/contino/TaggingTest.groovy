package uk.gov.hmcts.contino

import spock.lang.Shared
import spock.lang.Specification

class TaggingTest extends Specification {

  @Shared
      pipeline

  def setup() {
    pipeline = Stub(JenkinsStepMock.class)
  }

  def "Determine next tag to be applied"() {
    when:
      def util = buildUtilFactory("master", "SUCCESS")

    then:
      util.nextTag() == "1.0.16"
  }

  def "Default next tag should be 0.1.0 if no previous tags"() {
    when:
      def util = buildUtilFactory("master", "SUCCESS", "")
    then:
      util.nextTag() == "0.1.0"
  }

  def buildUtilFactory(String branch, String buildStatus, String lastTag = "1.0.15") {
    pipeline.env >> [ "BRANCH_NAME": branch, "PATH" : "" ]
    pipeline.currentBuild >>  [ "currentResult" : buildStatus, "result":buildStatus ]
    pipeline.sh(_) >> { cmd ->
      if (cmd.toString().contains("git describe"))
        lastTag
      else if (cmd.toString().contains("git tag --list"))
        lastTag
      else
        ""
    }
    return new Tagging(pipeline)
  }

  def "SUCCESSFUL build on 'master' branch"() {
    when: "pipeline utility applyTag('1.0.16') is called"
      def applyResult = buildUtilFactory("master", "SUCCESS").applyTag("1.0.16")

    then: "Current build is tagged and message is printed"
      println(applyResult)
      assert applyResult.contains("1.0.16")
  }

  def "SUCCESSFUL build different branch than master"() {
    when: "pipeline utility applyTag('1.0.16') is called"
      def applyResult = buildUtilFactory("some-branch", "SUCCESS").applyTag("1.0.16")

    then: "Current build is not tagged but informative message is printed"
      println(applyResult)
      assert applyResult.contains("1.0.16")
  }

  def "unSUCCESSFUL build different on 'master' branch"() {
    when: "pipeline utility applyTag('1.0.16') is called"
      def applyResult = buildUtilFactory("master", "ERROR").applyTag("1.0.16")

    then: "Current build is not tagged"
      println(applyResult)
      assert applyResult.contains("1.0.16") && applyResult.toLowerCase().contains("no tagging")
  }

}
