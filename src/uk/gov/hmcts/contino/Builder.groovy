package uk.gov.hmcts.contino

interface Builder {
  def build()
  def test()
  def sonarScan()
  def smokeTest()
  def functionalTest()
  def performanceTest()
  def apiGatewayTest()
  def crossBrowserTest()
  def crossBrowserTest(String browser)
  def securityCheck()
  def addVersionInfo()
  def mutationTest()
  def fullFunctionalTest()
  def securityScan()
  def runProviderVerification()
  def runConsumerTests()

  /**
   * Setup any required versions of a tool
   * This is run before any code is built
   *
   * i.e. detect java version for java, or use a tool like nvm to setup the nodejs version
   * @return
   */
  def setupToolVersion()
}
