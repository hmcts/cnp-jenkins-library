package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties

class YarnBuilder extends AbstractBuilder {

  private static final String INSTALL_CHECK_FILE = '.yarn_dependencies_installed'
  private static final String NVMRC = '.nvmrc'
  private static final String CVE_KNOWN_ISSUES_FILE_PATH = 'yarn-audit-known-issues'

  YarnBuilder(steps) {
    super(steps)
  }

  def build() {
    yarn("lint")

    addVersionInfo()
  }

  def fortifyScan() {
    yarn("fortifyScan")
  }

  def test() {
    yarn("test")
    runYarn("test:coverage")
    runYarn("test:a11y")
  }

  def sonarScan() {
    String properties = SonarProperties.get(steps)

    yarn("sonar-scan ${properties}")
  }

  def highLevelDataSetup(String dataSetupEnvironment) {
    yarn("highLevelDataSetup ${dataSetupEnvironment}")
  }

  def smokeTest() {
    try {
      yarn("test:smoke")
    } finally {
      steps.junit allowEmptyResults: true, testResults: 'smoke-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'smoke-output/**'
    }
  }

  def functionalTest() {
    try {
      yarn("test:functional")
    } finally {
      steps.junit allowEmptyResults: true, testResults: 'functional-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def apiGatewayTest() {
    try {
      yarn("test:apiGateway")
    } finally {
      steps.junit allowEmptyResults: true, testResults: 'api-output/*result.xml'
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

  def crossBrowserTest(String browser) {
    try {
      steps.withSauceConnect("reform_tunnel") {
        steps.sh("BROWSER_GROUP=$browser yarn test:crossbrowser")
      }
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: "functional-output/$browser*/*"
      steps.saucePublisher()
    }
  }

  def fullFunctionalTest(){
    try{
      yarn("test:fullfunctional")
    }
    finally {
      steps.junit allowEmptyResults: true, testResults: 'functional-output/**/*result.xml'
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
    steps.writeFile(file: 'yarn-audit-with-suppressions.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/yarn/yarn-audit-with-suppressions.sh'))
    try {
      steps.sh """
        set +ex
        export NVM_DIR='/home/jenkinsssh/.nvm' # TODO get home from variable
        . /opt/nvm/nvm.sh || true
        nvm install
        set -ex

        chmod +x yarn-audit-with-suppressions.sh

        ./yarn-audit-with-suppressions.sh
    """
    } finally {
      String issues = steps.readFile('yarn-audit-issues-result')
      String knownIssues = null
      if (steps.fileExists(CVE_KNOWN_ISSUES_FILE_PATH)) {
        knownIssues = steps.readFile(CVE_KNOWN_ISSUES_FILE_PATH)
      }

      def cveReport = prepareCVEReport(issues, knownIssues)

      CVEPublisher.create(steps)
        .publishCVEReport('node', cveReport)
    }
  }

  def prepareCVEReport(String issues, String knownIssues) {
    def jsonSlurper = new JsonSlurper()
    List<Object> issuesParsed = issues.split( '\n' ).collect { jsonSlurper.parseText(it) }

    Object summary = issuesParsed.find { it.type == 'auditSummary' }
    issuesParsed.removeIf { it.type == 'auditSummary' }

   issuesParsed = issuesParsed.collect {
      mapYarnAuditToOurReport(it)
    }

    List<Object> knownIssuesParsed = []
    if (knownIssues) {
      knownIssuesParsed = knownIssues.split('\n').collect {
        mapYarnAuditToOurReport(jsonSlurper.parseText(it))
      }
    }

    def result = [
      vulnerabilities: issuesParsed,
      summary        : summary.data
    ]

    if (!knownIssuesParsed.isEmpty()) {
      result["suppressed"] = knownIssuesParsed
    }

    return result
  }

  /**
   * We trim the report down to a 2MB per document limit in CosmosDB
   * On large projects we've seen reports that are ~15MB
   */
  private static LinkedHashMap<String, Object> mapYarnAuditToOurReport(it) {
    [
      title              : it?.data?.advisory?.title,
      cves               : it?.data?.advisory?.cves,
      vulnerable_versions: it?.data?.advisory?.vulnerable_versions,
      patched_versions   : it?.data?.advisory?.patched_versions,
      severity           : it?.data?.advisory?.severity,
      cwe                : it?.data?.advisory?.cwe,
      url                : it?.data?.advisory?.url,
      module_name        : it?.data?.advisory?.module_name
    ]
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

  def runConsumerCanIDeploy() {
    steps.sh("yarn test:can-i-deploy:consumer")
  }

  private runYarn(task){
    if (steps.fileExists(NVMRC)) {
      steps.sh """
        set +ex
        export NVM_DIR='/home/jenkinsssh/.nvm' # TODO get home from variable
        . /opt/nvm/nvm.sh || true
        nvm install
        set -ex

        yarn ${task}
      """
    } else {
      steps.sh("yarn ${task}")
    }
  }

  private runYarnQuiet(task) {
    def status = steps.sh(script: "yarn ${task} 1> /dev/null 2> /dev/null", returnStatus: true)
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
    super.setupToolVersion()
  }

}
