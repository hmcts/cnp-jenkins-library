package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class AppPipelineConfigTest extends Specification {

  AppPipelineConfig pipelineConfig
  AppPipelineDsl dsl
  PipelineCallbacksConfig callbacks

  def setup() {
    pipelineConfig = new AppPipelineConfig()
    callbacks = new PipelineCallbacksConfig()
    dsl = new AppPipelineDsl(callbacks, pipelineConfig)
  }

  def "ensure securityScan can be set in steps"() {
    when:
      dsl.enableSecurityScan()

    then:
      assertThat(pipelineConfig.securityScan).isEqualTo(true)
      assertThat(pipelineConfig.securityScanTimeout).isEqualTo(120)
  }
}
