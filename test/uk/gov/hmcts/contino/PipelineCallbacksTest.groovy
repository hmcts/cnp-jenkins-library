package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class PipelineCallbacksTest extends Specification {

  def steps
  def pipelineCallbacks

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [:]
    pipelineCallbacks = new PipelineCallbacks(null, steps)
  }

  def "ensure ENV_SUFFIX can be set"() {
    when:
    pipelineCallbacks.deployToV2Environments()

    then:
    assertThat(this.steps.env.ENV_SUFFIX).isEqualTo('v2')
  }

  def "ensure ENV_SUFFIX is not set by default"() {
    when:
    null

    then:
    assertThat(this.steps.env.ENV_SUFFIX).isNull()
  }

  def "ensure securityScan can be set in steps"() {
    when:
    pipelineCallbacks.enableSecurityScan()

    then:
    assertThat(pipelineCallbacks.securityScan).isEqualTo(true)
  }
}
