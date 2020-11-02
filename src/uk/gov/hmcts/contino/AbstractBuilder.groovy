package uk.gov.hmcts.contino

abstract class AbstractBuilder implements Builder, Serializable {

  def steps
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
  def setupToolVersion() {

    //Setting Up JAVA_HOME here as its used for Sonar scan even in YarnBuilder.
    if (steps.fileExists("/usr/lib/jvm/java-11-openjdk")) {
      steps.env.JAVA_HOME = "/usr/lib/jvm/java-11-openjdk"
      steps.env.PATH = "${steps.env.JAVA_HOME}/bin:${steps.env.PATH}"
    } else if (steps.fileExists("/usr/local/openjdk-11")) {
      steps.env.JAVA_HOME = "/usr/local/openjdk-11"
      steps.env.PATH = "${steps.env.JAVA_HOME}/bin:${steps.env.PATH}"
    }

  }
}
