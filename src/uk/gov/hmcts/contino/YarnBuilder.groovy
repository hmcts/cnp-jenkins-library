package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate

class YarnBuilder extends AbstractBuilder {

  private static final String INSTALL_CHECK_FILE = '.yarn_dependencies_installed'
  private static final String NVMRC = '.nvmrc'
  private static final String CVE_KNOWN_ISSUES_FILE_PATH = 'yarn-audit-known-issues'

  YarnBuilder(steps) {
    super(steps)
  }

  def SecurityScan(steps) {
    super(steps)
    this.steps = steps
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

    steps.sh "sonar-scanner ${properties}"
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
        yarn("test:crossbrowser", "BROWSER_GROUP=$browser")
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
    boolean yarnV2OrNewer = isYarnV2OrNewer()
    try {
      steps.sh """
        set +ex
        export NVM_DIR='/home/jenkinsssh/.nvm' # TODO get home from variable
        . /opt/nvm/nvm.sh || true
        nvm install
        set -ex
      """

      if (yarnV2OrNewer) {
        corepackEnable()
        steps.writeFile(file: 'yarn-audit-with-suppressions.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/yarn/yarnV2OrNewer-audit-with-suppressions.sh'))
      } else {
        steps.writeFile(file: 'yarn-audit-with-suppressions.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/yarn/yarn-audit-with-suppressions.sh'))
      }

      steps.sh """
        if ${yarnV2OrNewer}; then
          export PATH=\$HOME/.local/bin:\$PATH
        fi
        chmod +x yarn-audit-with-suppressions.sh
        ./yarn-audit-with-suppressions.sh
      """
    } finally {
      if (yarnV2OrNewer) {
        steps.sh """
          cat yarn-audit-result | jq -c '. | {type: "auditSummary", data: .metadata}' > yarn-audit-issues-result-summary
          cat yarn-audit-result | jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' >> yarn-audit-issues-advisories
          cat yarn-audit-issues-result-summary yarn-audit-issues-advisories > yarn-audit-issues-result
        """
      }
      String issues = steps.readFile('yarn-audit-issues-result')
      String knownIssues = null
      if (steps.fileExists(CVE_KNOWN_ISSUES_FILE_PATH)) {
        knownIssues = steps.readFile(CVE_KNOWN_ISSUES_FILE_PATH)
      }
      def cveReport = prepareCVEReport(issues, knownIssues)
      new CVEPublisher(steps)
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
      yarn("test:pact:verify-and-publish", "PACT_BROKER_URL=${pactBrokerUrl} PACT_PROVIDER_VERSION=${version}")
    } else {
      yarn("test:pact:verify", "PACT_BROKER_URL=${pactBrokerUrl} PACT_PROVIDER_VERSION=${version}")
    }
  }

  /**
   * Triggers a yarn hook
   * @param pactBrokerUrl the url of the pact broker
   * @param version the version of the current project, usually a git commit hash
   * @return
   */
  def runConsumerTests(pactBrokerUrl, version) {
    yarn("test:pact:run-and-publish", "PACT_BROKER_URL=${pactBrokerUrl} PACT_CONSUMER_VERSION=${version}")
  }

  def runConsumerCanIDeploy() {
    yarn("test:can-i-deploy:consumer")
  }

  private runYarn(String task, String prepend = ""){
    if (prepend && !prepend.endsWith(' ')) {
      prepend += ' '
    }
    boolean yarnV2OrNewer = isYarnV2OrNewer()

    if (steps.fileExists(NVMRC)) {
      steps.sh """
        set +ex
        export NVM_DIR='/home/jenkinsssh/.nvm' # TODO get home from variable
        . /opt/nvm/nvm.sh || true
        nvm install
        set -ex

        if ${yarnV2OrNewer}; then
          export PATH=\$HOME/.local/bin:\$PATH
        fi

        if ${prepend.toBoolean()}; then
          ${prepend}yarn ${task}
        else
          yarn ${task}
        fi
      """
    } else {
      steps.sh("""
        if ${yarnV2OrNewer}; then
          export PATH=\$HOME/.local/bin:\$PATH
        fi

        if ${prepend.toBoolean()}; then
          ${prepend}yarn ${task}
        else
          yarn ${task}
        fi
      """)
    }
  }

  private runYarnQuiet(String task, String prepend = "") {
    if (prepend && !prepend.endsWith(' ')) {
      prepend += ' '
    }
    boolean yarnV2OrNewer = isYarnV2OrNewer()
    def status = steps.sh(script: """
      if ${yarnV2OrNewer}; then
        export PATH=\$HOME/.local/bin:\$PATH
      fi

      if ${prepend.toBoolean()}; then
        ${prepend}yarn ${task} 1> /dev/null 2> /dev/null
      else
        yarn ${task} 1> /dev/null 2> /dev/null
      fi
    """, returnStatus: true)
    steps.echo("yarnQuiet ${task} -> ${status}")
    return status == 0  // only a 0 return status is success
  }

  private isYarnV2OrNewer() {
    def status = steps.sh label: "Determine if is yarn v1", script: '''
                ! grep packageManager package.json | grep yarn@[2-9]
          ''', returnStatus: true
    return status
  }

  private nagAboutOldYarnVersions() {
     if (!isYarnV2OrNewer()){
       WarningCollector.addPipelineWarning("old_yarn_version", "Please upgrade to Yarn V3, see https://moj.enterprise.slack.com/files/T1L0WSW9F/F04784SLAJC?origin_team=T1L0WSW9F", LocalDate.of(2023, 04, 26))
    }
  }

  private corepackEnable() {
    def status = steps.sh label: "corepack enable", script: '''
      mkdir -p \$HOME/.local/bin
      corepack enable  --install-directory \$HOME/.local/bin
    ''', returnStatus: true
    return status
  }

  def yarn(String task, String prepend = "") {
    boolean yarnV2OrNewer = isYarnV2OrNewer()
    if (!steps.fileExists(INSTALL_CHECK_FILE)) {
      steps.sh("touch ${INSTALL_CHECK_FILE}")
      if (yarnV2OrNewer) {
        corepackEnable()
        boolean zeroInstallEnabled = steps.fileExists(".yarn/cache")
        if (!zeroInstallEnabled) {
          runYarn("install")
        }
      } else if (!runYarnQuiet("check")) {
        runYarn("--mutex network install --frozen-lockfile")
      }
    }
    runYarn(task, prepend)
  }

  @Override
  def securityScan() {
    this.securityScan = new SecurityScan(steps)


    // if (steps.fileExists("security.sh")) {
    //   WarningCollector.addPipelineWarning("security.sh_moved", "Please remove security.sh from root of repository, no longer needed as it has been moved to the Jenkins library", LocalDate.of(2023, 03, 29))
    // }
    // steps.writeFile(file: 'security.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/security/frontend/security.sh'))


  }

  @Override
  def setupToolVersion() {
    super.setupToolVersion()
    nagAboutOldYarnVersions()
  }

}
