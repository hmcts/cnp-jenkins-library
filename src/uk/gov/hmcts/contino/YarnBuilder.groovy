package uk.gov.hmcts.contino;

class YarnBuilder implements Builder, Serializable {

  def steps

  YarnBuilder(steps) {
    this.steps = steps
  }

  def build() {
    yarn("install")
    yarn("lint")
  }

  def test() {
    yarn("test")
    yarn("test:coverage")
    yarn("test:a11y")
  }

  def sonarScan() {
    if (steps.respondsTo('withSonarQubeEnv')) {
      steps.withSonarQubeEnv("SonarQube") {
        yarn("sonar-scan")
      }

      steps.timeout(time: 1, unit: 'SECOND') { // Just in case something goes wrong, pipeline will be killed after a timeout
        def qg = steps.waitForQualityGate()
        if (qg.status != 'OK') {
          steps.error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
      }
    }
    else {
      steps.echo "Sonarqube plugin not installed. Unable to run static analysis."
    }
  }

  def smokeTest() {
    yarn("test:smoke")
  }

  def securityCheck() {
    yarn("test:nsp")
  }

  def yarn(task){
    def node = steps.tool(name: 'Node-8', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation')
    steps.env.PATH = "${node}/bin:${steps.env.PATH}"
    steps.sh("yarn ${task}")
  }
}
