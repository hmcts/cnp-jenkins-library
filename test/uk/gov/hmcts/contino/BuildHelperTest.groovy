package uk.gov.hmcts.contino

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import uk.gov.hmcts.contino.BuildHelper

class BuildHelperTest extends Specification {

  @Shared steps

  def setup() {
    steps = Stub(JenkinsStepMock.class)

  }

  def "NextTagVersion"() {
    given:
      steps.sh(_) >> "1.0.15"
      BuildHelper builHelper = new BuildHelper(steps)

    expect:
      builHelper.nextTagVersion() == "1.0.16"
  }

  @Ignore
  def "Tag"() {
    given: "a Jenkins Step object"
      steps.env.BRANCH_NAME = "master"
      steps.currentBuild = [currentResult : "SUCCESS"]
      steps.sh(_) >> "1.0.15"

    and:
      def builHelper = new BuildHelper(steps)

    expect:
      builHelper.tag("1.0.16").contains("1.0.16")

  }

}
