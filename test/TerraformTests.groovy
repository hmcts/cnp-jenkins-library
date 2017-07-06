package uk.gov.hmcts.tests

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import uk.gov.hmcts.contino.Terraform

class TerraformTests  extends  Specification {


  @Shared steps
  @Shared terraform
  @Shared credentialsStep



  def setup() {
    steps = Mock(uk.gov.hmcts.tests.JenkinsStepMock)
    credentialsStep = Mock(uk.gov.hmcts.tests.JenkinsCredentialsStepMock)
    steps.env >> [ "PATH" : ""]

    credentialsStep.withCredentials(_, _) >> {}

    terraform = new Terraform(steps, credentialsStep , "test")

  }


  @Ignore("can't test this with the anonymous function")
  def "should run init"() {

    when:


      terraform.init("dev", "", "", "")

    then:

      1 * steps.sh(_)


  }
}


