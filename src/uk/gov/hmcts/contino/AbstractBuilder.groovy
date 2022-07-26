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

  // some tests only issue relates to https://issues.jenkins.io/browse/JENKINS-47355
  def getSteps() {
    return steps
  }

  @Override
  def performanceTest() {
    executeGatling()
  }

  def executeGatling() {
    this.gatling.execute()
  }

  @Override
  def securityScan(){
    this.securitytest.execute()
  }

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
