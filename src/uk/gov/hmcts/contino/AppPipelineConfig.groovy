package uk.gov.hmcts.contino

class AppPipelineConfig extends CommonPipelineConfig implements Serializable {
  Map<String, List<Map<String, Object>>> vaultSecrets = [:]
  Map<String, String> vaultEnvironmentOverrides = ['preview':'aat', 'dev':'stg']
  String vaultName
  boolean migrateDb = false
  String dbMigrationVaultName

  boolean performanceTest = false
  boolean apiGatewayTest = false
  boolean crossBrowserTest = false
  List<String> parallelCrossBrowsers = []
  boolean mutationTest = false
  boolean fullFunctionalTest = false
  boolean securityScan = false
  boolean serviceApp = true
  boolean aksStagingDeployment = false
  boolean legacyDeployment = true
  Set<String> legacyDeploymentExemptions = []
  boolean pactBrokerEnabled = false
  boolean pactProviderVerificationsEnabled = false
  boolean pactConsumerTestsEnabled = false
  boolean pactConsumerCanIDeployEnabled = false
  boolean highLevelDataSetup = false
  boolean fortifyScan = false
  boolean clearHelmRelease = false
  String fortifyVaultName
  String s2sServiceName

  int crossBrowserTestTimeout
  int perfTestTimeout
  int apiGatewayTestTimeout
  int mutationTestTimeout
  int fullFunctionalTestTimeout
  int securityScanTimeout

  boolean legacyDeploymentForEnv(String environment) {
    return legacyDeployment && !legacyDeploymentExemptions.contains(environment)
  }
}
