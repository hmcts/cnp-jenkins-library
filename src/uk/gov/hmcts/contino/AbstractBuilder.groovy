package uk.gov.hmcts.contino

abstract class AbstractBuilder implements Builder, Serializable {

  public steps
  def gatling
  def securitytest
  def slackAlerts

  AbstractBuilder(steps) {
    this.steps = steps
    this.gatling = new Gatling(this.steps)
    this.securitytest = new SecurityScan(this.steps)
    this.slackAlerts = SlackAlerts
  }

  @Override
  def performanceTest(Script script) {
    executeGatling(script)
  }

  def executeGatling(Script script) {
    this.gatling.execute()
    slackAlerts.slack_message(script, "U08Q19ZJS8G", "warning", "I am here in abstract and execute gatlin function")
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
