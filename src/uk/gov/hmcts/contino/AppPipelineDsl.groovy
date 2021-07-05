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

  void loadVaultSecrets(Map<String, List<Map<String, Object>>> vaultSecrets) {
    config.vaultSecrets = vaultSecrets
  }

  void enableDbMigration(String dbMigrationVaultName = "") {
    if (dbMigrationVaultName == "") {
      WarningCollector.addPipelineWarning("deprecated_enable_db_migration_no_vault)", "enableDbMigration() is deprecated, please use enableDbMigration(<vault-name>)", new Date().parse("dd.MM.yyyy", "05.09.2019"))
    }

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

  void enableCrossBrowserTest(List<String> browsers, int timeout = 120) {
    config.crossBrowserTestTimeout = timeout
    config.parallelCrossBrowsers = browsers
  }

  void enableSecurityScan(int timeout = 120) {
    config.securityScanTimeout = timeout
    config.securityScan = true
  }

  @Deprecated // no longer required, kept so that builds don't break if they contain it
  void enableDockerBuild() {
    WarningCollector.addPipelineWarning("docker_build_enabled", "enableDockerBuild() is deprecated and no longer does anything, please remove this flag before the pipeline starts failing.,", new Date().parse("dd.MM.yyyy", "18.02.2020"))
  }

  void installCharts() {
    WarningCollector.addPipelineWarning("install_charts", "installCharts() is deprecated and no longer does anything, please remove this flag before the pipeline starts failing.,", new Date().parse("dd.MM.yyyy", "25.05.2020"))
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

  void disableLegacyDeploymentOnAAT() {
    config.legacyDeploymentExemptions.add("aat")
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

  enum PactRoles { CONSUMER, PROVIDER, CONSUMER_DEPLOY_CHECK}

  void enablePactAs(List<PactRoles> roles) {
    config.pactBrokerEnabled = true
    config.pactConsumerTestsEnabled = roles.contains(PactRoles.CONSUMER)
    config.pactProviderVerificationsEnabled = roles.contains(PactRoles.PROVIDER)
    config.pactConsumerCanIDeployEnabled = roles.contains(PactRoles.CONSUMER_DEPLOY_CHECK)
  }

  void enableHighLevelDataSetup() {
    config.highLevelDataSetup = true
  }

  void enableFortifyScan(String fortifyVaultName = "") {
    config.fortifyScan = true
    config.fortifyVaultName = fortifyVaultName
  }
}
