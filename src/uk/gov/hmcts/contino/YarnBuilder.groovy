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
    try {
      yarn("test:smoke")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './smoke-output/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'smoke-output/*'
    }
  }

  def functionalTest() {
    try {
      yarn("test:functional")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './functional-output/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/*'
    }
  }

  def securityCheck() {
    yarn("test:nsp")
  }

  def yarn(task){
    steps.sh("yarn ${task}")
  }
}
