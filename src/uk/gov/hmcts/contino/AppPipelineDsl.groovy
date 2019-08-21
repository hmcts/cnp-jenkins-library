package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.deprecation.WarningCollector

class AppPipelineDsl extends CommonPipelineDsl implements Serializable {
  final AppPipelineConfig config
  def final steps

  AppPipelineDsl(Object steps, PipelineCallbacksConfig callbacks, AppPipelineConfig config) {
    super(steps, callbacks, config)
    this.config = config
    this.steps = steps
  }


  @Deprecated
  void loadVaultSecrets(List<Map<String, Object>> vaultSecrets) {
    WarningCollector.addPipelineWarning("deprecated_load_vault_secrets", "loadVaultSecrets(List<Map<String, Object>> vaultSecrets) is deprecated, see https://github.com/hmcts/cnp-jenkins-library#secrets-for-functional--smoke-testing ", new Date().parse("dd.MM.yyyy", "27.08.2019"))
    config.vaultSecrets = ['unknown': vaultSecrets]
  }

  void loadVaultSecrets(Map<String, List<Map<String, Object>>> vaultSecrets) {
    config.vaultSecrets = vaultSecrets
  }

  @Deprecated
  void setVaultName(String vaultName) {
    config.vaultName = vaultName
    WarningCollector.addPipelineWarning("deprecated_set_vault_name", "setVaultName() is deprecated, see https://github.com/hmcts/cnp-jenkins-library#secrets-for-functional--smoke-testing ", new Date().parse("dd.MM.yyyy", "27.08.2019"))
  }

  void enableDbMigration(String dbMigrationVaultName = "") {
    config.migrateDb = true
    config.dbMigrationVaultName = dbMigrationVaultName
  }

  void enablePerformanceTest(int timeout = 15) {
    config.perfTestTimeout = timeout
    config.performanceTest = true
  }

  void enableApiGatewayTest(int timeout = 15) {
    config.apiGatewayTestTimeout = timeout
    config.apiGatewayTest = true
  }

  void enableCrossBrowserTest(int timeout = 120) {
    config.crossBrowserTestTimeout = timeout
    config.crossBrowserTest = true
  }

  void enableSecurityScan(int timeout = 120) {
    config.securityScanTimeout = timeout
    config.securityScan = true
  }

  void enableDockerBuild() {
    config.dockerBuild = true
  }

  @Deprecated
  void enableDeployToAKS() {
    WarningCollector.addPipelineWarning("deprecated_enable_deployto_AKS", "enableDeployToAKS() is deprecated, use installCharts instead ", new Date().parse("dd.MM.yyyy", "27.08.2019"))
    config.deployToAKS = true
    config.installCharts = false
  }

  void installCharts() {
    config.installCharts = true
    config.deployToAKS = false
  }

  void enableFullFunctionalTest(int timeout = 30) {
    config.fullFunctionalTestTimeout = timeout
    config.fullFunctionalTest = true
  }

  void enableMutationTest(int timeout = 120) {
    config.mutationTestTimeout = timeout
    config.mutationTest = true
  }

  void disableLegacyDeployment() {
    config.legacyDeployment = false
  }

  void nonServiceApp() {
    config.serviceApp = false
  }

  void overrideVaultEnvironments(Map<String, String> vaultOverrides) {
    config.vaultEnvironmentOverrides = vaultOverrides
  }

  void enableAksStagingDeployment() {
    config.aksStagingDeployment = true
  }

  enum PactRoles { CONSUMER, PROVIDER }

  void enablePactAs(List<PactRoles> roles) {
    config.pactBrokerEnabled = true
    config.pactConsumerTestsEnabled = roles.contains(PactRoles.CONSUMER)
    config.pactProviderVerificationsEnabled = roles.contains(PactRoles.PROVIDER)
  }

}
