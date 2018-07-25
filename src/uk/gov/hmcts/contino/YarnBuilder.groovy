package uk.gov.hmcts.contino;


class YarnBuilder extends AbstractBuilder {

  YarnBuilder(steps) {
    super(steps)
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

  def crossBrowserTest() {
    try {
      sh("sauce('reform_tunnel') {"+
        +"sauceconnect(options: \"--verbose --tunnel-identifier reformtunnel\", useGeneratedTunnelIdentifier: false, verboseLogging: true) {"+
      yarn("test:crossbrowser")+
        "}}")
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/cross-browser/*'
    }
  }

  def mutationTest() {
    try{
      yarn("test:mutation")
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/cross-browser/*'
    }
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
    steps.sh("yarn ${task}")
  }
}
