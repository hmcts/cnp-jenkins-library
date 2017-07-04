package uk.gov.hmcts.tests

import groovy.json.JsonOutput
import spock.lang.Shared
import spock.lang.Specification
import uk.gov.hmcts.contino.Terraform


class TerraformTests  extends  Specification {


  @Shared steps
  @Shared terraform



  def setup() {
    steps = Mock(uk.gov.hmcts.tests.JenkinsStepMock)
    steps.env >> [ "PATH" : ""]


    terraform = new Terraform(steps, "test")

  }

  def "should run init"() {

    when:
       terraform.init("dev", "", "", "")

    then:
      1 * steps.sh(_)


  }
}


