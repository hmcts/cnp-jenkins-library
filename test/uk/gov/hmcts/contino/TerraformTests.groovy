package uk.gov.hmcts.contino


import spock.lang.Shared
import spock.lang.Ignore
import spock.lang.Specification

class TerraformTests  extends  Specification {

  @Shared steps
  @Shared terraform
  @Shared credentialsStep

  def setup() {
    steps = Mock(JenkinsStepMock)
    steps.libraryResource(_) >> new File("./resources/uk/gov/hmcts/contino/state-storage.json").text
    steps.env >> [ "PATH" : ""]

    credentialsStep.withCredentials(_, _) >> {}

    terraform = new Terraform(steps, "test")
  }

  @Ignore("WIP")
  def "should run init"() {
    when:
      terraform.plan("dev")

    then:
      1 * steps.sh(_)

  }
}


