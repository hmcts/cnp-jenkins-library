package uk.gov.hmcts.contino

class GradleBuilder implements Builder, Serializable {

  def steps
  def product

  GradleBuilder(steps, product) {
    this.steps = steps
    this.product = product
  }

  def build() {
    gradle("build")
    steps.stash(name: product, includes: "build/libs/*.jar")
  }

  def test() {
    gradle("test")
  }

  def sonarScan() {
    if (Jenkins.instance.getPluginManager().getPlugins().find { it.getShortName() == 'sonar' } != null) {
      steps.withSonarQubeEnv("SonarQube") {
        // requires SonarQube Scanner for Gradle 2.1+
        // It's important to add --info because of SONARJNKNS-281
        gradle("--info sonarqube")
      }

      steps.timeout(time: 30, unit: 'SECONDS') {
        // Just in case something goes wrong, pipeline will be killed after a timeout
        def qg = steps.waitForQualityGate()
        if (qg.status != 'OK') {
          steps.error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
      }
    }
    else {
      steps.echo "Sonarqube plugin not installed. Skipping static analysis."
    }
  }

  def smokeTest() {

  }

  def securityCheck() {

  }

  def gradle(String task) {
    steps.sh("./gradlew ${task}")
  }
}
