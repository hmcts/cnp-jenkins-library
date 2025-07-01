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
    script.echo "YR - inside abstract performancetests"
    executeGatling(script)
  }

  def executeGatling(Script script) {
    this.gatling.execute()
    script.echo "YR - inside abstract executeGatling"
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
