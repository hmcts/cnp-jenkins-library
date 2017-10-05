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

  def smokeTest() {
    yarn("test:smoke")
  }

  def yarn(task){
    def node = steps.tool(name: 'Node-8', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation')
    env.PATH = "${node}/bin:${env.PATH}"
    steps.sh("yarn ${task}")
  }
}
