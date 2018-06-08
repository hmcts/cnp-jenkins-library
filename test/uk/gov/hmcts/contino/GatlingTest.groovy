package uk.gov.hmcts.contino

import spock.lang.Specification

class GatlingTest extends Specification {

  def steps
  def gatling

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    gatling = new Gatling(steps)
  }

  def "execute calls docker function with Gatling image and appropriate run args"() {
    when:
      gatling.execute()
    then:
    1 * steps.withDocker(Gatling.GATLING_IMAGE, Gatling.GATLING_RUN_ARGS, _ as Closure)
  }

}
