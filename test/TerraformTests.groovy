package uk.gov.hmcts.tests

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import uk.gov.hmcts.contino.Terraform

class TerraformTests  extends  Specification {


  @Shared steps
  @Shared terraform



  def setup() {
    steps = Mock(uk.gov.hmcts.tests.JenkinsStepMock)
    steps.env >> [ "PATH" : ""]
    def terr = {}
    steps.withCredentials(_, _) >> terr


    terraform = new Terraform(steps, "test")

  }

  @Ignore("WIP")
  def "should run init"() {

    when:
       terraform.init("dev", "", "", "")

    then:
      terr()
      1 * steps.sh(_)


  }
}


