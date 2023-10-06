package uk.gov.hmcts.contino

class AppPipelineConfig extends CommonPipelineConfig implements Serializable {
  Map<String, List<Map<String, Object>>> vaultSecrets = [:]
  Map<String, String> vaultEnvironmentOverrides = ['preview':'aat', 'dev':'stg']
  String vaultName
  boolean migrateDb = false
  String dbMigrationVaultName
  String urlExclusions
  String securityRules = httpRequest url: "https://raw.githubusercontent.com/hmcts/security-test-rules/master/conf/security-rules.conf", httpMode: 'GET', acceptType: 'APPLICATION_JSON'

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

  boolean legacyDeploymentForEnv(String environment) {
    return legacyDeployment && !legacyDeploymentExemptions.contains(environment)
  }
}
