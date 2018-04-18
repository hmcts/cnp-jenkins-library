package uk.gov.hmcts.contino

class AngularBuilder implements Builder, Serializable {

  def steps

  YarnBuilder builder

  AngularBuilder(steps) {
    this.steps = steps
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
  def securityCheck() {
    builder.securityCheck()
  }
}
