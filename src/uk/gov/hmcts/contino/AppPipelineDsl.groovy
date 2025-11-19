package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class AppPipelineDsl extends CommonPipelineDsl implements Serializable {
  def final config
  def final steps

  AppPipelineDsl(steps, callbacks, config) {
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

  void enablePerformanceTest(int timeout = 15, perfGatlingAlerts=false, perfRerunOnFail=false) {
    config.perfTestTimeout = timeout
    config.performanceTest = true
    config.perfGatlingAlerts = perfGatlingAlerts
    config.perfRerunOnFail = perfRerunOnFail
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

  void enableSecurityScan(Map<String, Object> params = [:]) {
    def configuration = [
        cookieIgnoreList: "",
        alertFilters: "",
        urlExclusions: "",
        timeout: 120,
        scanType: "auto"
    ] << params

    config.securityScanCookieIgnoreList = configuration.cookieIgnoreList
    config.securityScanAlertFilters = configuration.alertFilters
    config.securityScanUrlExclusions = configuration.urlExclusions
    config.securityScanType = configuration.scanType
    config.securityScanTimeout = configuration.timeout
    config.securityScan = true
  }

  void enableFullFunctionalTest(int timeout = 30) {
    config.fullFunctionalTestTimeout = timeout
    config.fullFunctionalTest = false  //true **
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
    WarningCollector.addPipelineWarning("Helm-ReleaseonSuccess-deprecation", "`enableCleanupOfHelmReleaseOnSuccess` is now the default, please remove it from your `Jenkinsfile`.", LocalDate.of(2023, 9, 15));
  }

  void enableCleanupOfHelmReleaseOnFailure() {
    WarningCollector.addPipelineWarning("Helm-ReleaseonFailure-deprecation", "`enableCleanupOfHelmReleaseOnFailure` is now the default, please remove it from your `Jenkinsfile`.", LocalDate.of(2023, 9, 15));
  }

  void enableCleanupOfHelmReleaseAlways() {
    config.clearHelmReleaseOnFailure = true;
    WarningCollector.addPipelineWarning("Helm-ReleaseAlways-deprecation", "`enableCleanupOfHelmReleaseAlways` is now the default, please remove it from your `Jenkinsfile`.", LocalDate.of(2023, 9, 15));
  }

  void disableCleanupOfHelmReleaseOnFailure() {
    config.clearHelmReleaseOnFailure = false;
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

  void enablePerformanceTestStages(Map params = [:]) {
    config.performanceTestStages = true
    config.performanceTestStagesTimeout = params.timeout ?: 15
    config.performanceTestConfigPath = params.configPath
  }

  void enableSrgEvaluation(Map params = [:]) {
    config.srgEvaluation = true
    config.srgServiceName = params.serviceName ?: null
    config.srgFailureBehavior = params.failureBehavior ?: 'warn'
  }

  void enableGatlingLoadTests(Map params = [:]) {
    config.gatlingLoadTests = true
    config.gatlingLoadTestTimeout = params.timeout ?: 10
    config.gatlingRepo = params.repo
    config.gatlingBranch = params.branch ?: 'master'
    config.gatlingSimulation = params.simulation

    if (!config.gatlingRepo) {
      throw new IllegalArgumentException("enableGatlingLoadTests: 'repo' parameter is required")
    }
  }

  void enableIdamTestUser(Map params = [:]) {
    config.idamTestUser = true
    config.idamTestUserEmail = params.email
    config.idamTestUserForename = params.forename
    config.idamTestUserSurname = params.surname
    config.idamTestUserPassword = params.password
    config.idamTestUserRoles = params.roles ?: []
  }
}
