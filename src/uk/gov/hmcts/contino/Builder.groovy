package uk.gov.hmcts.contino

interface Builder {
  def build()
  def fortifyScan()
  def test()
  def sonarScan()
  def highLevelDataSetup(String dataSetupEnvironment)
  def smokeTest()
  def functionalTest()
  def performanceTest()
  def apiGatewayTest()
  def crossBrowserTest()
  def securityCheck()
  def addVersionInfo()
  def mutationTest()
  def fullFunctionalTest()
  def securityScan()
  def runProviderVerification()
  def runConsumerTests()
  def runConsumerCanIDeploy()

  /**
   * Setup any required versions of a tool
   * This is run before any code is built
   *
   * i.e. detect java version for java, or use a tool like nvm to setup the nodejs version
   * @return
   */
  def setupToolVersion()
}
