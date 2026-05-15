package uk.gov.hmcts.pipeline

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class AgentSelectorTest extends Specification {

  def "labelForEnvironment should use normalised environment as default label"() {
    expect:
    assertThat(AgentSelector.labelForEnvironment(environment)).isEqualTo(label)

    where:
    environment | label
    'sbox'      | 'ubuntu-sbox'
    'sandbox'   | 'ubuntu-sbox'
    'preview'   | 'ubuntu-preview'
    'aat'       | 'ubuntu-aat'
    'ithc'      | 'ubuntu-ithc'
    'perftest'  | 'ubuntu-perftest'
    'demo'      | 'ubuntu-demo'
    'prod'      | 'ubuntu-prod'
  }

  def "labelForEnvironment should preserve existing prefixed environment normalisation"() {
    expect:
    assertThat(AgentSelector.labelForEnvironment(environment)).isEqualTo(label)

    where:
    environment    | label
    'idam-aat'     | 'ubuntu-aat'
    'idam-preview' | 'ubuntu-preview'
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

  def "labelForEnvironment should use product and environment-specific override first"() {
    given:
    def envVars = [
      PRODUCT: 'civil',
      ENVIRONMENT_AGENT_LABEL_CIVIL_PREVIEW: 'civil-preview',
      ENVIRONMENT_AGENT_LABEL_PREVIEW: 'ubuntu-preview-override',
      ENVIRONMENT_AGENT_LABEL_TEMPLATE: 'jenkins-${environment}'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('preview', envVars)).isEqualTo('civil-preview')
  }

  def "labelForEnvironment should render product-specific label template"() {
    given:
    def envVars = [
      RAW_PRODUCT_NAME: 'civil',
      ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL: 'civil-${environment}'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('preview', envVars)).isEqualTo('civil-preview')
  }

  def "labelForEnvironment should use configured product agent label"() {
    given:
    def envVars = [
      PRODUCT: 'civil',
      PRODUCT_AGENT_LABEL: 'civil'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('preview', envVars)).isEqualTo('civil')
  }

  def "labelForEnvironmentWithoutProductFallback should ignore product-level labels"() {
    given:
    def envVars = [
      PRODUCT: 'toffee',
      PRODUCT_AGENT_LABEL: 'toffee-vm',
      ENVIRONMENT_AGENT_LABEL_TEMPLATE: 'ubuntu-${environment}'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironmentWithoutProductFallback('dev', envVars)).isEqualTo('ubuntu-dev')
  }

  def "labelForEnvironment should allow product argument to drive product-specific lookup"() {
    given:
    def envVars = [
      ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL: 'civil-{environment}'
    ]

    expect:
    assertThat(AgentSelector.labelForEnvironment('aat', envVars, 'civil')).isEqualTo('civil-aat')
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

  def "isEnvironmentLikeSubscription should identify env-scoped subscription names"() {
    expect:
    assertThat(AgentSelector.isEnvironmentLikeSubscription(subscription)).isEqualTo(expected)

    where:
    subscription | expected
    'dev'        | true
    'stg'        | true
    'prod'       | true
    'sbox'       | true
    'sandbox'    | true
    'nonprod'    | false
    'preview'    | false
    null         | false
  }

  def "isRunningOnEnvironmentAgent should compare current label with selected environment label"() {
    expect:
    assertThat(AgentSelector.isRunningOnEnvironmentAgent(envVars, environment, product)).isEqualTo(expected)

    where:
    envVars                                                                                       | environment | product  | expected
    [DEPLOYMENT_ENVIRONMENT: 'preview', BUILD_AGENT_TYPE: 'ubuntu-preview']                       | null        | ''       | true
    [DEPLOYMENT_ENVIRONMENT: 'preview', BUILD_AGENT_TYPE: 'civil-preview', PRODUCT: 'civil']      | null        | ''       | false
    [DEPLOYMENT_ENVIRONMENT: 'preview', BUILD_AGENT_TYPE: 'civil-preview',
      ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL: 'civil-${environment}']                             | 'preview'   | 'civil'  | true
    [DEPLOYMENT_ENVIRONMENT: 'preview', BUILD_AGENT_TYPE: 'ubuntu-aat']                           | 'preview'   | ''       | false
    [BUILD_AGENT_TYPE: 'ubuntu-preview']                                                          | null        | ''       | false
  }

  private static class ThrowingEnvVars {
    Object getAt(String key) {
      throw new RuntimeException("lookup failed")
    }
  }
}
