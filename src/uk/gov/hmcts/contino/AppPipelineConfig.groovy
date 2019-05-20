package uk.gov.hmcts.contino

class AppPipelineConfig extends CommonPipelineConfig implements Serializable {
  Map<String, List<Map<String, Object>>> vaultSecrets = [:]
  Map<String, String> vaultEnvironmentOverrides = ['preview':'aat']
  String vaultName
  boolean migrateDb = false

  boolean performanceTest = false
  boolean apiGatewayTest = false
  boolean crossBrowserTest = false
  boolean mutationTest = false
  boolean dockerBuild = false
  boolean deployToAKS = false
  boolean installCharts = false
  boolean fullFunctionalTest = false
  boolean securityScan = false
  boolean serviceApp = true
  boolean aksStagingDeployment = false
  boolean legacyDeployment = true
  boolean pactBrokerEnabled = false
  boolean pactProviderVerificationsEnabled = false
  boolean pactConsumerTestsEnabled = false

  int crossBrowserTestTimeout
  int perfTestTimeout
  int apiGatewayTestTimeout
  int mutationTestTimeout
  int fullFunctionalTestTimeout
  int securityScanTimeout
}
