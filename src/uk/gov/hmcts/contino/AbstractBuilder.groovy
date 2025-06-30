package uk.gov.hmcts.contino

abstract class AbstractBuilder implements Builder, Serializable {

  public steps
  def gatling
  def securitytest

  AbstractBuilder(steps) {
    this.steps = steps
    this.gatling = new Gatling(this.steps)
    this.securitytest = new SecurityScan(this.steps)
  }

  @Override
  def performanceTest() {
    executeGatling()
    SlackAlerts.slack_message("U08Q19ZJS8G", "warning", "1 Yogesh outputting values: ${this.steps.currentBuild.result}")
  }

  def executeGatling() {
    this.gatling.execute()
    SlackAlerts.slack_message("U08Q19ZJS8G", "warning", "2 Yogesh outputting values: ${currentBuild.result}")

  }

  @Override
  def securityScan(){
    this.securitytest.execute()
  }

  @Override
  def techStackMaintenance() {}

  @Override
  def runProviderVerification() {}

  @Override
  def runConsumerTests() {}

  @Override
  def runConsumerCanIDeploy() {}

  @Override
  def setupToolVersion() {
  }
}
