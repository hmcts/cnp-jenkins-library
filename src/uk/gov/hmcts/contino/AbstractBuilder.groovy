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

  protected def withPuppeteerCache(Closure body) {
    def cacheRoot = steps.env.WORKSPACE_TMP ?: steps.env.WORKSPACE ?: '.'
    steps.withEnv(["PUPPETEER_CACHE_DIR=${cacheRoot}/puppeteer-cache"]) {
      body()
    }
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
