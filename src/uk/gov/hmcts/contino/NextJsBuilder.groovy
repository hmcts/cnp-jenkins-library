package uk.gov.hmcts.contino

class NextJsBuilder extends AbstractBuilder {

  YarnBuilder builder

  NextJsBuilder(steps) {
    super(steps)
    this.builder = new YarnBuilder(steps)
  }

  @Override
  def build() {
    builder.build()
    builder.yarn("build")
    
    // Optional: Analyze bundle size if configured
    if (steps.fileExists("package.json")) {
      def packageJson = steps.readJSON file: 'package.json'
      if (packageJson.scripts?.analyze) {
        builder.yarn("analyze")
      }
    }
  }

  @Override
  def fortifyScan() {
    builder.fortifyScan()
  }

  @Override
  def test() {
    builder.test()
  }

  @Override
  def sonarScan() {
    builder.sonarScan()
  }

  @Override
  def highLevelDataSetup(String dataSetupEnvironment) {
    builder.highLevelDataSetup(dataSetupEnvironment)
  }

  @Override
  def smokeTest() {
    builder.smokeTest()
  }

  @Override
  def functionalTest() {
    builder.functionalTest()
  }

  @Override
  def performanceTest() {
    builder.performanceTest()
  }

  @Override
  def apiGatewayTest() {
    builder.apiGatewayTest()
  }

  @Override
  def securityCheck() {
    builder.securityCheck()
  }

  @Override
  def addVersionInfo() {
    builder.addVersionInfo()
  }

  @Override
  def crossBrowserTest() {
    builder.crossBrowserTest()
  }

  def crossBrowserTest(String browser) {
    builder.crossBrowserTest(browser)
  }

  @Override
  def mutationTest(){
    builder.mutationTest()
  }

  @Override
  def fullFunctionalTest(){
    builder.fullFunctionalTest()
  }

  @Override
  def setupToolVersion() {
    builder.setupToolVersion()
  }

  @Override
  def runProviderVerification(){
    builder.runProviderVerification()
  }

  def runProviderVerification(pactBrokerUrl, version, publish){
    builder.runProviderVerification(pactBrokerUrl, version, publish)
  }

  @Override
  def runConsumerTests(){
    builder.runConsumerTests()
  }

  def runConsumerTests(pactBrokerUrl, version){
    builder.runConsumerTests(pactBrokerUrl, version)
  }

  @Override
  def runConsumerCanIDeploy(){
    builder.runConsumerCanIDeploy()
  }

  @Override
  def securityScan(){
    builder.securityScan()
  }

  @Override
  def techStackMaintenance() {
    builder.techStackMaintenance()
  }
}
