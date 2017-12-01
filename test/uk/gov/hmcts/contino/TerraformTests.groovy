package uk.gov.hmcts.contino


import spock.lang.Shared
import spock.lang.Ignore
import spock.lang.Specification

class TerraformTests extends Specification {

  @Shared
      steps
  @Shared
      terraform

  def setup() {
    steps = Mock(JenkinsStepMock)
    steps.libraryResource(_) >> new File("./resources/uk/gov/hmcts/contino/state-storage.json").text
    steps.env >> ["PATH": ""]
  }

  def "should run init on master branch with [dev, test, prod] env"() {
    given:
    steps.env.BRANCH_NAME = 'master'
    terraform = new Terraform(steps, "test")

    when:
    terraform.plan("dev")
    then:
    1 * steps.fileExists(_)
    3 * steps.ansiColor('xterm', _)

    when:
    terraform.plan("test")
    then:
    1 * steps.fileExists(_)
    3 * steps.ansiColor('xterm', _)

  }

  def "should not run init on other branch with [dev, test, prod] env"() {
    given:
    steps.env.BRANCH_NAME = 'some_branch'
    terraform = new Terraform(steps, "test")

    when:
    terraform.apply("dev")
    then:
    thrown(Exception)

    when:
    terraform.apply("test")
    then:
    thrown(Exception)

    when:
    terraform.apply("prod")
    then:
    thrown(Exception)

  }


}


