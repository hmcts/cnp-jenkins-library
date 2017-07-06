package uk.gov.hmcts.tests

import groovy.json.JsonSlurper
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
    steps.libraryResource(_) >> new File("./resources/uk/gov/hmcts/contino/state-storage.json").text
    steps.env >> [ "PATH" : ""]

    credentialsStep.withCredentials(_, _) >> {}

    terraform = new Terraform(steps, "test")

  }



  def "should run init"() {

    when:


      terraform.init("dev")

    then:

      1 * steps.sh(_)


  }
}


