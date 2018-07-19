package uk.gov.hmcts.contino

class AngularBuilder extends AbstractBuilder {

  YarnBuilder builder

  AngularBuilder(steps) {
    super(steps)
    this.builder = new YarnBuilder(steps)
  }

  @Override
  def build() {
    builder.build()
    builder.yarn("build:ssr")
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

  @Override
  def mutationTest(){

  }
}
