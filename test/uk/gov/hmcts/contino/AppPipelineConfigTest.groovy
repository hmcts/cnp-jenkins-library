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

  def "ensure defaults"() {
    expect:
      assertThat(pipelineConfig.migrateDb).isFalse()
      assertThat(pipelineConfig.performanceTest).isFalse()
      assertThat(pipelineConfig.apiGatewayTest).isFalse()
      assertThat(pipelineConfig.crossBrowserTest).isFalse()
      assertThat(pipelineConfig.mutationTest).isFalse()
      assertThat(pipelineConfig.dockerBuild).isFalse()
      assertThat(pipelineConfig.deployToAKS).isFalse()
      assertThat(pipelineConfig.installCharts).isFalse()
      assertThat(pipelineConfig.fullFunctionalTest).isFalse()
      assertThat(pipelineConfig.securityScan).isFalse()
      assertThat(pipelineConfig.legacyDeployment).isTrue()
      assertThat(pipelineConfig.serviceApp).isTrue()
      assertThat(pipelineConfig.pactBrokerEnabled).isFalse()
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isFalse()
  }

  def "ensure securityScan can be set in steps"() {
    when:
      dsl.enableSecurityScan()

    then:
      assertThat(pipelineConfig.securityScan).isTrue()
      assertThat(pipelineConfig.securityScanTimeout).isEqualTo(120)
  }

  def "load vault secrets"() {
    given:
      def secrets = ['vault': [['secretName': 'name', 'var': 'var']]]
    when:
      dsl.loadVaultSecrets(secrets)
    then:
      assertThat(pipelineConfig.vaultSecrets).isEqualTo(secrets)
  }

  def "load vault secrets - deprecated"() {
    given:
      def secrets = [['secretName': 'name', 'var': 'var']]
    when:
      dsl.loadVaultSecrets(secrets)
    then:
      assertThat(pipelineConfig.vaultSecrets).isEqualTo(['unknown': secrets])
  }

  def "ensure enable db migration"() {
    when:
      dsl.enableDbMigration()
    then:
      assertThat(pipelineConfig.migrateDb).isTrue()
  }

  def "ensure enable performance test"() {
    when:
      dsl.enablePerformanceTest()
    then:
      assertThat(pipelineConfig.performanceTest).isTrue()
      assertThat(pipelineConfig.perfTestTimeout).isEqualTo(15)
  }

  def "ensure enable API gateway test"() {
    when:
      dsl.enableApiGatewayTest()
    then:
      assertThat(pipelineConfig.apiGatewayTest).isTrue()
      assertThat(pipelineConfig.apiGatewayTestTimeout).isEqualTo(15)
  }

  def "ensure enable cross browser test"() {
    when:
      dsl.enableCrossBrowserTest()
    then:
      assertThat(pipelineConfig.crossBrowserTest).isTrue()
      assertThat(pipelineConfig.crossBrowserTestTimeout).isEqualTo(120)
  }

  def "ensure enable docker build"() {
    when:
      dsl.enableDockerBuild()
    then:
      assertThat(pipelineConfig.dockerBuild).isTrue()
  }

  def "ensure enable deploy to AKS"() {
    when:
      dsl.enableDeployToAKS()
    then:
      assertThat(pipelineConfig.deployToAKS).isTrue()
      assertThat(pipelineConfig.installCharts).isFalse()
  }

  def "ensure install charts"() {
    when:
      dsl.installCharts()
    then:
      assertThat(pipelineConfig.installCharts).isTrue()
      assertThat(pipelineConfig.deployToAKS).isFalse()
  }

  def "ensure enable full functional test"() {
    when:
      dsl.enableFullFunctionalTest()
    then:
      assertThat(pipelineConfig.fullFunctionalTest).isTrue()
      assertThat(pipelineConfig.fullFunctionalTestTimeout).isEqualTo(30)
  }

  def "ensure enable mutation test"() {
    when:
      dsl.enableMutationTest()
    then:
      assertThat(pipelineConfig.mutationTest).isTrue()
      assertThat(pipelineConfig.mutationTestTimeout).isEqualTo(120)
  }

  def "ensure disable legacy deployment"() {
    when:
      dsl.disableLegacyDeployment()
    then:
      assertThat(pipelineConfig.legacyDeployment).isFalse()
  }

  def "ensure non service app"() {
    when:
    dsl.nonServiceApp()
    then:
    assertThat(pipelineConfig.serviceApp).isFalse()
  }

  def "ensure enable deploy to AKS Staging"() {
    when:
    dsl.enableAksStagingDeployment()
    then:
    assertThat(pipelineConfig.aksStagingDeployment).isTrue()
  }

  def "ensure enable pact broker deployment check"() {
    given:
      assertThat(pipelineConfig.pactBrokerEnabled).isFalse()
    when:
      dsl.enablePactAs([])
    then:
      assertThat(pipelineConfig.pactBrokerEnabled).isTrue()
  }

  def "ensure enable pact consumer tests"() {
    given:
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isFalse()
    when:
      dsl.enablePactAs([AppPipelineDsl.PactRoles.CONSUMER])
    then:
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isTrue()
  }

  def "ensure enable pact provider verification"() {
    given:
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
    when:
      dsl.enablePactAs([AppPipelineDsl.PactRoles.PROVIDER])
    then:
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isTrue()
  }

  def "ensure enable pact consumer tests and provider verification"() {
    given:
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
      assertThat(pipelineConfig.pactBrokerEnabled).isFalse()
    when:
      dsl.enablePactAs([
        AppPipelineDsl.PactRoles.CONSUMER,
        AppPipelineDsl.PactRoles.PROVIDER
      ])
    then:
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isTrue()
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isTrue()
  }

}
