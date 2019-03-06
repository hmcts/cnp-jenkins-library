package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class CommonPipelineConfigTest extends Specification {
  AppPipelineConfig pipelineConfig
  AppPipelineDsl dsl
  PipelineCallbacksConfig callbacks

  def setup() {
    pipelineConfig = new AppPipelineConfig()
    callbacks = new PipelineCallbacksConfig()
    dsl = new AppPipelineDsl(callbacks, pipelineConfig)
  }

  def "Register 'before' callback"() {
    when:
      Closure body = {
        System.out.println("hello world")
      }
      dsl.before("test", body)
    then:
      assertThat(callbacks.bodies.get("before:test")).isEqualTo(body)
  }

  def "Register 'after' callback"() {
    when:
      Closure body = {
        System.out.println("bye bye world")
      }
      dsl.after("test", body)
    then:
      assertThat(callbacks.bodies.get("after:test")).isEqualTo(body)
  }

  def "Register 'on stage failure' callback"() {
    when:
      Closure body = {
        System.out.println("Aaarrghhh!")
      }
      dsl.onStageFailure(body)
    then:
      assertThat(callbacks.bodies.get("onStageFailure")).isEqualTo(body)
  }

  def "Register 'on failure' callback"() {
    when:
    Closure body = {
      System.out.println("Aaarrghhh!")
    }
    dsl.onFailure(body)
    then:
    assertThat(callbacks.bodies.get("onFailure")).isEqualTo(body)
  }

  def "Register 'on success' callback"() {
    when:
      Closure body = {
        System.out.println("S'all good man!")
      }
      dsl.onSuccess(body)
    then:
      assertThat(callbacks.bodies.get("onSuccess")).isEqualTo(body)
  }
}
