package uk.gov.hmcts.pipeline

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class AgentSelectorTest extends Specification {

  def "labelForEnvironment should use normalised environment as default label"() {
    expect:
    assertThat(AgentSelector.labelForEnvironment(environment)).isEqualTo(label)

    where:
    environment    | label
    'aat'          | 'ubuntu-aat'
    'idam-aat'     | 'ubuntu-aat'
    'sandbox'      | 'ubuntu-sbox'
    'packer-prod'  | 'ubuntu-prod'
    'vault-demo'   | 'ubuntu-demo'
  }

  def "labelForEnvironment should use environment-specific override first"() {
    given:
    def envVars = [
      ENVIRONMENT_AGENT_LABEL_AAT: 'jenkins-aat-template',
      ENVIRONMENT_AGENT_LABEL_TEMPLATE: 'jenkins-${environment}'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('aat', envVars)).isEqualTo('jenkins-aat-template')
  }

  def "labelForEnvironment should use build agent type fallback"() {
    given:
    def envVars = [
      BUILD_AGENT_TYPE_PROD: 'legacy-prod-template',
      ENVIRONMENT_AGENT_LABEL_TEMPLATE: 'jenkins-${environment}'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('prod', envVars)).isEqualTo('legacy-prod-template')
  }

  def "labelForEnvironment should render configured label template"() {
    given:
    def envVars = [
      ENVIRONMENT_AGENT_LABEL_TEMPLATE: 'jenkins-${environment}-agent'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('perftest', envVars)).isEqualTo('jenkins-perftest-agent')
  }

  def "labelForEnvironment should render brace-only label template"() {
    given:
    def envVars = [
      ENVIRONMENT_AGENT_LABEL_TEMPLATE: 'jenkins-{environment}-agent'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('demo', envVars)).isEqualTo('jenkins-demo-agent')
  }

  def "labelForEnvironment should return empty label for missing environment"() {
    expect:
    assertThat(AgentSelector.labelForEnvironment(environment)).isEqualTo('')

    where:
    environment << [null, '']
  }

  def "labelForEnvironment should fall back when env lookup throws"() {
    expect:
    assertThat(AgentSelector.labelForEnvironment('aat', new ThrowingEnvVars())).isEqualTo('ubuntu-aat')
  }

  private static class ThrowingEnvVars {
    Object getAt(String key) {
      throw new RuntimeException("lookup failed")
    }
  }
}
