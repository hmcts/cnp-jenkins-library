package uk.gov.hmcts.contino

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

  void enableDbMigration(String dbMigrationVaultName) {
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

  void enableCleanupOfHelmReleaseOnSuccess() {
    config.clearHelmReleaseOnSuccess = true;
    WarningCollector.addPipelineWarning ("helm_cleanup_failure", "The CleanupOfHelmReleaseOnSuccess function will be depricated, please use the enable-helm label to keep helm resources", LocalDate.of (2023, 8, 14))
  }

  void enableCleanupOfHelmReleaseOnFailure() {
    config.clearHelmReleaseOnFailure = true;
  }

  void enableCleanupOfHelmReleaseAlways() {
    config.clearHelmReleaseOnSuccess = true;
    config.clearHelmReleaseOnFailure = true;
    WarningCollector.addPipelineWarning ("helm_cleanup_failure", "The enableCleanupOfHelmReleaseAlways function will be depricated, please use the enable-helm label to keep helm resources", LocalDate.of (2023, 8, 14))
  }
  }

  enum PactRoles { CONSUMER, PROVIDER, CONSUMER_DEPLOY_CHECK}

  void enablePactAs(List<PactRoles> roles) {
    config.pactBrokerEnabled = true
    config.pactConsumerTestsEnabled = roles.contains(PactRoles.CONSUMER)
    config.pactProviderVerificationsEnabled = roles.contains(PactRoles.PROVIDER)
    config.pactConsumerCanIDeployEnabled = roles.contains(PactRoles.CONSUMER_DEPLOY_CHECK)
  }

  void enableHighLevelDataSetup(String highLevelDataSetupKeyvaultName = "") {
    config.highLevelDataSetup = true
    config.highLevelDataSetupKeyVaultName = highLevelDataSetupKeyvaultName
  }

  void enableFortifyScan(String fortifyVaultName = "") {
    config.fortifyScan = true
    config.fortifyVaultName = fortifyVaultName
  }

  void enableDockerTestBuild() {
    config.dockerTestBuild = true
  }

}
