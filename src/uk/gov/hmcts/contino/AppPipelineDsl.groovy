package uk.gov.hmcts.contino

class AppPipelineDsl extends CommonPipelineDsl implements Serializable {
  final AppPipelineConfig config

  AppPipelineDsl(PipelineCallbacksConfig callbacks, AppPipelineConfig config) {
    super(callbacks, config)
    this.config = config
  }


  @Deprecated
  void loadVaultSecrets(List<Map<String, Object>> vaultSecrets) {
    config.vaultSecrets = ['unknown': vaultSecrets]
  }

  void loadVaultSecrets(Map<String, List<Map<String, Object>>> vaultSecrets) {
    config.vaultSecrets = vaultSecrets
  }

  @Deprecated
  void setVaultName(String vaultName) {
    config.vaultName = vaultName
  }

  void enableDbMigration() {
    config.migrateDb = true
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

  void enableDeployToAKS() {
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

}
