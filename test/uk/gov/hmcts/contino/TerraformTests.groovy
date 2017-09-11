package uk.gov.hmcts.contino


import spock.lang.Shared
import spock.lang.Ignore
import spock.lang.Specification

class TerraformTests  extends  Specification {

  @Shared steps
  @Shared terraform

  def setup() {
    steps = Mock(JenkinsStepMock)
    steps.libraryResource(_) >> new File("./resources/uk/gov/hmcts/contino/state-storage.json").text
    steps.env >> [ "PATH" : ""]
//    steps.ansiColor(_, _) >> { format, closure -> println "Format: "+ format+ "; Chained command"+ closure.metaClass.classNode.getDeclaredMethods("doCall")[0].code.text  }

    terraform = new Terraform(steps, "test")
  }

  def "should run init"() {
    when:
      terraform.plan("dev")

    then:
      1 * steps.fileExists(_)
      3 * steps.ansiColor('xterm', _ )
  }
}


