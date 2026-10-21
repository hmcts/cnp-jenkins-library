package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class PipelineCallBacksRunnerTest extends Specification {

  def "records explicit outcomes independently of the build result"() {
    given:
      def publisher = Mock(MetricsPublisher)
      def runner = new PipelineCallbacksRunner(new PipelineCallbacksConfig(), publisher)

    when:
      runner.callAround('test') {}

    then:
      1 * publisher.publishStep('test', 'STARTED', _ as String)
      1 * publisher.publishStep('test', 'SUCCESS', _ as String, null)
  }

  def "records and propagates a failed step"() {
    given:
      def publisher = Mock(MetricsPublisher)
      def runner = new PipelineCallbacksRunner(new PipelineCallbacksConfig(), publisher)
      def failure = new IllegalStateException('test failed')

    when:
      runner.callAround('test') { throw failure }

    then:
      thrown(IllegalStateException)
      1 * publisher.publishStep('test', 'STARTED', _ as String)
      1 * publisher.publishStep('test', 'FAILURE', _ as String, failure)
  }

  def "records a failure outside callbacks when wrapping a stage"() {
    given:
      def publisher = Mock(MetricsPublisher)
      def runner = new PipelineCallbacksRunner(new PipelineCallbacksConfig(), publisher)
      def failure = new IllegalStateException('agent lost during stash back')

    when:
      runner.recordStep('AAT build promotion') {
        runner.callAround('AAT:promotion') {}
        throw failure
      }

    then:
      thrown(IllegalStateException)
      1 * publisher.publishStep('AAT:promotion', 'STARTED', _ as String)
      1 * publisher.publishStep('AAT:promotion', 'SUCCESS', _ as String, null)
      1 * publisher.publishStep('AAT build promotion', 'STARTED', _ as String)
      1 * publisher.publishStep('AAT build promotion', 'FAILURE', _ as String, failure)
  }

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
