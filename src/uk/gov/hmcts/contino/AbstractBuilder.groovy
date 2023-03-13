package uk.gov.hmcts.contino

abstract class AbstractBuilder implements Builder, Serializable {

  public steps
  def gatling
  def securitytest

  AbstractBuilder(steps) {
    this.steps = steps
    this.gatling = new Gatling(this.steps)
    this.securitytest = new SecurityScan(this.steps)
    this.securitytestfrontend = new SecurityScanFrontend(this.steps)
    this.securitytestbackend = new SecurityScanBackend(this.steps)
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
  def securityScanFrontend(){
    this.securitytest.execute()
  }

  @Override
  def securityScanBackend(){
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
