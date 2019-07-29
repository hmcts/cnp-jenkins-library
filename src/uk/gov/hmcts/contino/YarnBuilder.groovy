package uk.gov.hmcts.contino;

class YarnBuilder extends AbstractBuilder {

  private static final String INSTALL_CHECK_FILE = '.yarn_dependencies_installed'

  YarnBuilder(steps) {
    super(steps)
  }

  def build() {
    yarn("lint")

    addVersionInfo()
  }

  def test() {
    yarn("test")
    runYarn("test:coverage")
    runYarn("test:a11y")
  }

  def sonarScan() {
    yarn('sonar-scan')
  }

  def smokeTest() {
    try {
      yarn("test:smoke")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './smoke-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'smoke-output/**'
    }
  }

  def functionalTest() {
    try {
      yarn("test:functional")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './functional-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def apiGatewayTest() {
    try {
      yarn("test:apiGateway")
    } finally {
      steps.junit allowEmptyResults: true, testResults: './api-output/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'api-output/*'
    }
  }

  def crossBrowserTest() {
    try {
      steps.withSauceConnect("reform_tunnel") {
        yarn("test:crossbrowser")
      }
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/crossbrowser/reports/**/*'
      steps.saucePublisher()
    }
  }

  def fullFunctionalTest(){
    try{
      yarn("test:fullfunctional")
    }
    finally {
      steps.junit allowEmptyResults: true, testResults: './functional-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def mutationTest() {
    try{
      yarn("test:mutation")
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

  /**
   * Triggers a yarn hook
   * @param pactBrokerUrl the url of the pact broker
   * @param version the version of the current project, usually a git commit hash
   * @param publish should the provider verification publish the results
   * @return
   */
  def runProviderVerification(pactBrokerUrl, version, publish) {
    if (publish) {
      steps.sh("PACT_BROKER_URL=${pactBrokerUrl} PACT_PROVIDER_VERSION=${version} yarn test:pact:verify-and-publish")
    } else {
      steps.sh("PACT_BROKER_URL=${pactBrokerUrl} PACT_PROVIDER_VERSION=${version} yarn test:pact:verify")
    }
  }

  /**
   * Triggers a yarn hook
   * @param pactBrokerUrl the url of the pact broker
   * @param version the version of the current project, usually a git commit hash
   * @return
   */
  def runConsumerTests(pactBrokerUrl, version) {
    steps.sh("PACT_BROKER_URL=${pactBrokerUrl} PACT_CONSUMER_VERSION=${version} yarn test:pact:run-and-publish")
  }

  private runYarn(task){
    steps.sh("yarn ${task}")
  }

  private runYarnQuiet(task) {
    def status = steps.sh(script: "yarn ${task} &> /dev/null", returnStatus: true)
    steps.echo("yarnQuiet ${task} -> ${status}")
    return status == 0  // only a 0 return status is success
  }

  def yarn(task) {
    if (!steps.fileExists(INSTALL_CHECK_FILE) && !runYarnQuiet("check")) {
      runYarn("--mutex network install --frozen-lockfile")
      steps.sh("touch ${INSTALL_CHECK_FILE}")
    }
    runYarn(task)
  }

  @Override
  def setupToolVersion() {
    // TODO setup nvm support here, PRs welcome
  }

}
