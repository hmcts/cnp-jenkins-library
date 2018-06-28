package uk.gov.hmcts.contino

class CrossBrowserPipelineSteps extends AbstractNightlyBuilder {

  CrossBrowserPipelineSteps(steps) {
    super(steps)
  }

  def build() {
    yarn("install")
  }

  def crossBrowserTest() {
      //sauceconnect(options: "-u divorce -K e0067992-049e-412c-9d15-2566a28cfb73 --verbose --tunnel-identifier reformtunnel", verboseLogging: true)
      yarn("test:crossbrowser")


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



