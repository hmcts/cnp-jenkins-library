package uk.gov.hmcts.contino;

class YarnBuilder implements Builder, Serializable {

  def steps

  YarnBuilder(steps) {
    this.steps = steps
  }

  def build() {
    yarn("install")
    yarn("lint")

    addVersionInfo()
  }

  def test() {
    yarn("test")
    yarn("test:coverage")
    yarn("test:a11y")
  }

  def sonarScan() {
    yarn('sonar-scan')
  }

  def smokeTest() {
    yarn("test:smoke")
  }

  def e2eTest() {
    yarn("test:e2e")
  }

  def securityCheck() {
    yarn("test:nsp")
  }

  @Override
  def addVersionInfo() {
    steps.sh '''tee version <<EOF
version: $(node -pe 'require("./package.json").version')
number: ${BUILD_NUMBER}
commit: $(git rev-parse HEAD)
date: $(date)
EOF
    '''
  }

  def yarn(task){
    def node = steps.tool(name: 'Node-8', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation')
    steps.env.PATH = "${node}/bin:${steps.env.PATH}"
    steps.sh("yarn ${task}")
  }
}
