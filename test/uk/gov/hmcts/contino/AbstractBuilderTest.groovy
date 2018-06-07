package uk.gov.hmcts.contino

import spock.lang.Specification

class AbstractBuilderTest extends Specification {

  def steps
  def builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    builder = new BuilderImpl(steps)
  }

  def "performanceTest calls 'gradle gatlingRun' with '--rerun-tasks' flag"() {
    when:
      builder.performanceTest()
    then:
      1 * steps.withDocker(AbstractBuilder.GATLING_IMAGE, AbstractBuilder.GATLING_RUN_ARGS, _ as Closure)
  }

  class BuilderImpl extends AbstractBuilder {
    BuilderImpl(steps) {
      super(steps)
    }

    @Override
    def build() {
      return null
    }

    @Override
    def test() {
      return null
    }

    @Override
    def sonarScan() {
      return null
    }

    @Override
    def smokeTest() {
      return null
    }

    @Override
    def functionalTest() {
      return null
    }

    @Override
    def securityCheck() {
      return null
    }

    @Override
    def addVersionInfo() {
      return null
    }
  }
}
