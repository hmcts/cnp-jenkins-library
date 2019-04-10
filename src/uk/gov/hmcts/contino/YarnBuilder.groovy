package uk.gov.hmcts.contino;

class YarnBuilder extends AbstractBuilder {

  private static final String INSTALL_CHECK_FILE = '.yarn_dependencies_installed'

  YarnBuilder(steps) {
    super(steps)
  }

  def build() {
    yarn("--mutex network install --frozen-lockfile")
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
      yarnWithCheck("test:smoke")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './smoke-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'smoke-output/**'
    }
  }

  def functionalTest() {
    try {
      yarnWithCheck("test:functional")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './functional-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def apiGatewayTest() {
    try {
      yarnWithCheck("test:apiGateway")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './api-output/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'api-output/*'
    }
  }

  def crossBrowserTest() {
    try {
      steps.withSauceConnect("reform_tunnel") {
        yarnWithCheck("test:crossbrowser")
      }
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/crossbrowser/reports/**/*'
      steps.saucePublisher()
    }
  }

  def fullFunctionalTest(){
    try{
      yarnWithCheck("test:fullfunctional")
    }
    finally {
      steps.junit allowEmptyResults: true, testResults: './functional-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def mutationTest() {
    try{
      yarnWithCheck("test:mutation")
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
    }
  }

  def securityCheck() {
    // no-op
    // to be replaced with yarn audit once suppressing vulnerabilities is possible 
    // https://github.com/yarnpkg/yarn/issues/6669
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

  def yarnQuiet(task) {
    return steps.sh(script: "yarn ${task} &> /dev/null", returnStatus: true)
  }

  def yarnWithCheck(task) {
    if (!steps.fileExists(INSTALL_CHECK_FILE) && !yarnQuiet("check")) {
      yarn("--mutex network install --frozen-lockfile")
      steps.sh("touch ${INSTALL_CHECK_FILE}")
    }
    yarn(task)
  }

}
