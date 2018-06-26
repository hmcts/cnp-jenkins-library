package uk.gov.hmcts.contino

class CrossBrowserPipelineSteps extends AbstractNightlyBuilder {

  CrossBrowserPipelineSteps(steps) {
      super(steps)
    }

  def build() {
    yarn("install")
    yarn("lint")
  }

  def crossBrowserTest() {
    yarn("test-crossbrowser-e2e")
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
    steps.sh("yarn ${task}")
  }

}



