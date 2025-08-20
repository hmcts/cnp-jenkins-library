package uk.gov.hmcts.contino

class AppPipelineConfig extends CommonPipelineConfig implements Serializable {
  Map<String, List<Map<String, Object>>> vaultSecrets = [:]
  Map<String, String> vaultEnvironmentOverrides = ['preview':'aat', 'dev':'stg']
  String vaultName
  boolean migrateDb = false
  String dbMigrationVaultName
  String securityScanUrlExclusions
  String securityScanType
  String securityScanAlertFilters
  String securityScanCookieIgnoreList

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
  boolean clearHelmReleaseOnFailure = true
  String fortifyVaultName
  String s2sServiceName
  String highLevelDataSetupKeyVaultName
  boolean dockerTestBuild = false

  int crossBrowserTestTimeout
  int perfTestTimeout = 15
  int apiGatewayTestTimeout
  int mutationTestTimeout
  int fullFunctionalTestTimeout = 30
  int securityScanTimeout = 120

  //For Performance Test Pipelines
  boolean perfGatlingAlerts = false
  boolean perfRerunOnFail = false
  String perfSlackChannel = "#performance-alerts"

  // Performance test stages configuration
  boolean performanceTestStages = false
  int performanceTestStagesTimeout
  String performanceTestConfigPath

  // Gatling load test configuration
  boolean gatlingLoadTests = false
  int gatlingLoadTestTimeout = 10
  String gatlingRepo
  String gatlingBranch = 'master'
  String gatlingTestPath = 'src/test/scala'
  String gatlingSimulation
  int gatlingUsers = 5
  String gatlingRampDuration = '30s'
  String gatlingTestDuration = '300s'


  boolean legacyDeploymentForEnv(String environment) {
    return legacyDeployment && !legacyDeploymentExemptions.contains(environment)
  }
}
