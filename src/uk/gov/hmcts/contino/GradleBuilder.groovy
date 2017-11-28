package uk.gov.hmcts.contino

class GradleBuilder implements Builder, Serializable {

  def steps
  def product
  Versioner versioner

  GradleBuilder(steps, product) {
    this.steps = steps
    this.product = product
    this.versioner = new Versioner(steps)
  }

  def build() {
    versioner.addJavaVersionInfo()
    gradle("build")
    steps.stash(name: product, includes: "build/libs/*.jar")
  }

  def test() {
    gradle("test")
  }

  def sonarScan() {
      gradle("--info sonarqube")
  }

  def smokeTest() {

  }

  def securityCheck() {

  }

  def gradle(String task) {
    steps.sh("./gradlew ${task}")
  }
}
