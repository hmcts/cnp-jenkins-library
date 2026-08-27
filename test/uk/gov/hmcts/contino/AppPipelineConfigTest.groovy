package uk.gov.hmcts.contino

import spock.lang.Specification
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

import static org.assertj.core.api.Assertions.assertThat

class AppPipelineConfigTest extends Specification {

  AppPipelineConfig pipelineConfig
  AppPipelineDsl dsl
  PipelineCallbacksConfig callbacks
  def steps

  def setup() {
    WarningCollector.pipelineWarnings.clear()
    pipelineConfig = new AppPipelineConfig()
    callbacks = new PipelineCallbacksConfig()
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [ : ]
    dsl = new AppPipelineDsl(steps, callbacks, pipelineConfig)

  }

  def cleanup() {
    WarningCollector.pipelineWarnings.clear()
  }

  def "ensure defaults"() {
    expect:
      assertThat(pipelineConfig.migrateDb).isFalse()
      assertThat(pipelineConfig.e2eTest).isFalse()
      assertThat(pipelineConfig.performanceTest).isFalse()
      assertThat(pipelineConfig.apiGatewayTest).isFalse()
      assertThat(pipelineConfig.crossBrowserTest).isFalse()
      assertThat(pipelineConfig.parallelCrossBrowsers).isEqualTo([])
      assertThat(pipelineConfig.mutationTest).isFalse()
      assertThat(pipelineConfig.fullFunctionalTest).isFalse()
      assertThat(pipelineConfig.securityScan).isFalse()
      assertThat(pipelineConfig.legacyDeployment).isTrue()
      assertThat(pipelineConfig.serviceApp).isTrue()
      assertThat(pipelineConfig.deployableApp).isTrue()
      assertThat(pipelineConfig.libraryBranchAllowed).isFalse()
      assertThat(pipelineConfig.releaseOnMerge).isFalse()
      assertThat(pipelineConfig.pactBrokerEnabled).isFalse()
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isFalse()
      assertThat(pipelineConfig.pactConsumerCanIDeployEnabled).isFalse()
  }

  def "ensure securityScan can be set in steps"() {
    when:
      dsl.enableSecurityScan()
    then:
      assertThat(pipelineConfig.securityScanCookieIgnoreList).isEqualTo("")
      assertThat(pipelineConfig.securityScanAlertFilters).isEqualTo("")
      assertThat(pipelineConfig.securityScanUrlExclusions).isEqualTo("")
      assertThat(pipelineConfig.securityScanType).isEqualTo("auto")
      assertThat(pipelineConfig.securityScanTimeout).isEqualTo(120)
      assertThat(pipelineConfig.securityScan).isTrue()
  }

  def "load vault secrets"() {
    given:
      def secrets = ['vault': [['secretName': 'name', 'var': 'var']]]
    when:
      dsl.loadVaultSecrets(secrets)
    then:
      assertThat(pipelineConfig.vaultSecrets).isEqualTo(secrets)
  }

  def "ensure enable e2e test"() {
    when:
    dsl.enableE2eTest()
    then:
    assertThat(pipelineConfig.e2eTest).isTrue()
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

  def "ensure enable parallel cross browser test"() {
    when:
      dsl.enableCrossBrowserTest(['chrome', 'firefox', 'safari', 'microsoft'])
    then:
      assertThat(pipelineConfig.parallelCrossBrowsers).isEqualTo(['chrome', 'firefox', 'safari', 'microsoft'])
      assertThat(pipelineConfig.crossBrowserTestTimeout).isEqualTo(120)
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
      assertThat(pipelineConfig.legacyDeploymentForEnv("aat")).isFalse()
      assertThat(pipelineConfig.legacyDeploymentForEnv("prod")).isFalse()
      assertThat(pipelineConfig.legacyDeploymentForEnv("sandbox")).isFalse()
  }

  def "ensure disable legacy deployment on AAT"() {
    when:
      dsl.disableLegacyDeploymentOnAAT()
    then:
      assertThat(pipelineConfig.legacyDeploymentExemptions).containsExactly("aat")
      assertThat(pipelineConfig.legacyDeploymentForEnv("aat")).isFalse()
      assertThat(pipelineConfig.legacyDeploymentForEnv("prod")).isTrue()
      assertThat(pipelineConfig.legacyDeploymentForEnv("sandbox")).isTrue()
  }

  def "ensure disable legacy deployment overrides AAT"() {
    when:
      dsl.disableLegacyDeployment()
      dsl.disableLegacyDeploymentOnAAT()
    then:
      assertThat(pipelineConfig.legacyDeployment).isFalse()
      assertThat(pipelineConfig.legacyDeploymentForEnv("aat")).isFalse()
      assertThat(pipelineConfig.legacyDeploymentForEnv("prod")).isFalse()
      assertThat(pipelineConfig.legacyDeploymentForEnv("sandbox")).isFalse()
  }

  def "ensure non service app"() {
    when:
    dsl.nonServiceApp()
    then:
    assertThat(pipelineConfig.serviceApp).isFalse()
  }

  def "ensure is non deployable app"() {
    when:
    dsl.nonDeployableApp()
    then:
    assertThat(pipelineConfig.deployableApp).isFalse()
    assertThat(pipelineConfig.serviceApp).isFalse()
  }
  def "ensure release on merge"() {
    when:
    dsl.releaseOnMerge()
    then:
    assertThat(pipelineConfig.releaseOnMerge).isTrue()
  }

  def "ensure enable deploy to AKS Staging"() {
    when:
    dsl.enableAksStagingDeployment()
    then:
    assertThat(pipelineConfig.aksStagingDeployment).isTrue()
  }

  def "ensure clear helm release is set"() {
    when:
    dsl.disableCleanupOfHelmReleaseOnFailure()
    then:
    assertThat(pipelineConfig.clearHelmReleaseOnFailure).isFalse()
  }

    def "ensure enable high level data setup"() {
        when:
        dsl.enableHighLevelDataSetup()
        then:
        assertThat(pipelineConfig.highLevelDataSetup).isTrue()
        assertThat(pipelineConfig.highLevelDataSetupKeyVaultName).isEqualTo("")
        assertThat(pipelineConfig.highLevelDataSetupEnvironments).isNull()
    }

    def "warn that legacy high level data setup configuration is deprecated"() {
      when:
      dsl.enableHighLevelDataSetup()
      then:
      assertThat(WarningCollector.pipelineWarnings).hasSize(1)
      assertThat(WarningCollector.pipelineWarnings[0].warningKey).isEqualTo('deprecated_high_level_data_setup_legacy_configuration')
      assertThat(WarningCollector.pipelineWarnings[0].deprecationDate).isEqualTo(LocalDate.of(2026, 10, 17))
    }

    def "ensure enable high level data setup with highLevelDataSetupKeyVaultName"() {
        when:
        dsl.enableHighLevelDataSetup("highLevelDataSetupKeyVaultName")
        then:
        assertThat(pipelineConfig.highLevelDataSetup).isTrue()
        assertThat(pipelineConfig.highLevelDataSetupKeyVaultName).isEqualTo("highLevelDataSetupKeyVaultName")
    }

    def "ensure enable high level data setup with skipHighLevelDataSetupProd"() {
      when:
      dsl.enableHighLevelDataSetup("", true)
      then:
      assertThat(pipelineConfig.highLevelDataSetup).isTrue()
      assertThat(pipelineConfig.highLevelDataSetupKeyVaultName).isEqualTo("")
      assertThat(pipelineConfig.skipHighLevelDataSetupProd).isEqualTo(true)
    }

    def "ensure enable high level data setup for configured environments"() {
      when:
      dsl.enableHighLevelDataSetup(['pr', ' staging ', 'AAT', 'PROD'], 'custom-key-vault')
      then:
      assertThat(pipelineConfig.highLevelDataSetup).isTrue()
      assertThat(pipelineConfig.highLevelDataSetupKeyVaultName).isEqualTo('custom-key-vault')
      assertThat(pipelineConfig.highLevelDataSetupEnvironments).containsExactlyInAnyOrder('PR', 'STAGING', 'AAT', 'PROD')
      assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    def "reject unsupported high level data setup environments"() {
      when:
      dsl.enableHighLevelDataSetup(['PR', 'DEMO'])
      then:
      def exception = thrown(IllegalArgumentException)
      assertThat(exception.message).contains('DEMO')
    }

    def "ensure enable fortify scan without fortifyVaultName"() {
    when:
    dsl.enableFortifyScan()
    then:
    assertThat(pipelineConfig.fortifyScan).isTrue()
    assertThat(pipelineConfig.fortifyVaultName).isEqualTo("")
  }

  def "ensure enable fortify scan with fortifyVaultName"() {
    when:
    dsl.enableFortifyScan("fortifyVaultName")
    then:
    assertThat(pipelineConfig.fortifyScan).isTrue()
    assertThat(pipelineConfig.fortifyVaultName).isEqualTo("fortifyVaultName")
  }

  def "ensure enable slack notifications"() {
    def slackChannel = "#donotdisturb"
    when:
    dsl.enableSlackNotifications(slackChannel)
    then:
    assertThat(pipelineConfig.slackChannel).isEqualTo(slackChannel)
    assertThat(steps.env.BUILD_NOTICE_SLACK_CHANNEL).isEqualTo(slackChannel)
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
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
    when:
      dsl.enablePactAs([AppPipelineDsl.PactRoles.CONSUMER])
    then:
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isTrue()
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
  }

  def "ensure enable pact provider verification"() {
    given:
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isFalse()
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isFalse()
    when:
      dsl.enablePactAs([AppPipelineDsl.PactRoles.PROVIDER])
    then:
      assertThat(pipelineConfig.pactProviderVerificationsEnabled).isTrue()
      assertThat(pipelineConfig.pactConsumerTestsEnabled).isFalse()
  }

  def "ensure enable pact can i deploy"() {
    given:
    assertThat(pipelineConfig.pactConsumerCanIDeployEnabled).isFalse()
    when:
    dsl.enablePactAs([AppPipelineDsl.PactRoles.CONSUMER_DEPLOY_CHECK])
    then:
    assertThat(pipelineConfig.pactConsumerCanIDeployEnabled).isTrue()
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
      assertThat(pipelineConfig.pactConsumerCanIDeployEnabled).isFalse()
  }

}
