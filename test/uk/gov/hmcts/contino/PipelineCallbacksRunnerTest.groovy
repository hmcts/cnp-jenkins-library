package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class PipelineCallBacksRunnerTest extends Specification {

  def "ensure correctly registered call before is called before body"() {
    given:
      PipelineCallbacksConfig config = new PipelineCallbacksConfig()
      StringBuilder text = new StringBuilder()
      config.registerBefore('build') {
        text.append('callback')
      }
      PipelineCallbacksRunner pcr = new PipelineCallbacksRunner(config)
    when:
      pcr.callAround('build') {
          text.append('body')
      }
    then:
      assertThat(text.toString()).isEqualTo('callbackbody')
  }

  def "ensure deprecated call after  throws exception"() {
    given:
      PipelineCallbacksConfig config = new PipelineCallbacksConfig()
      StringBuilder text = new StringBuilder()
      config.registerAfter('build') {
        text.append('callback')
      }
      PipelineCallbacksRunner pcr = new PipelineCallbacksRunner(config)
    when:
      pcr.callAround('build') {
          text.append('body')
      }
    then:
      thrown(RuntimeException)
  }

  def "ensure that afterAll callbacks are called correctly" () {
    given:
      PipelineCallbacksConfig config = new PipelineCallbacksConfig()
      StringBuilder text = new StringBuilder()
      config.registerAfterAll() { stage ->
        text.append('afterAll')
      }
      PipelineCallbacksRunner pcr = new PipelineCallbacksRunner(config)
    when:
      pcr.callAround('checkout') {
          text.append('checkout')
      }
      pcr.callAround('build') {
          text.append('build')
      }
    then:
      assertThat(text.toString()).isEqualTo('checkoutafterAllbuildafterAll')
  }

  def "ensure that callbacks with different names are not called" () {
    given:
      PipelineCallbacksConfig config = new PipelineCallbacksConfig()
      StringBuilder text = new StringBuilder()
      config.registerAfter('build') {
        text.append('callback')
      }
      config.registerBefore('build') {
        text.append('callback')
      }
      PipelineCallbacksRunner pcr = new PipelineCallbacksRunner(config)
    when:
      pcr.callAround('checkout') {
          text.append('body')
      }
    then:
      assertThat(text.toString()).isEqualTo('body')
  }

  def "ensure stage name is passed into callback"() {
    given:
      PipelineCallbacksConfig config = new PipelineCallbacksConfig()
      StringBuilder text = new StringBuilder()
      config.registerBefore('build') { stage ->
        text.append(stage.toUpperCase())
      }
      PipelineCallbacksRunner pcr = new PipelineCallbacksRunner(config)
    when:
      pcr.callAround('build') {
          text.append('body')
      }
    then:
      assertThat(text.toString()).isEqualTo('BUILDbody')
  }

  def "ensure callbacks aren't called during registration"() {
    given:
      PipelineCallbacksConfig config = new PipelineCallbacksConfig()
      boolean beforeBuildCalled = false
    when:
      config.registerBefore('build') {
        beforeBuildCalled = true
      }
    then:
      assertThat(beforeBuildCalled).isFalse()
  }

}
