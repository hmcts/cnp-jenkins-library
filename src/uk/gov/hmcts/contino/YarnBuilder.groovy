package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.ardoq.ArdoqClient
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate
import static java.lang.Float.valueOf

class YarnBuilder extends AbstractBuilder {

  private static final String INSTALL_CHECK_FILE = '.yarn_dependencies_installed'
  private static final String NVMRC = '.nvmrc'
  private static final Float DESIRED_MIN_VERSION = 18.16
  private static final LocalDate NODEJS_EXPIRATION = LocalDate.of(2023, 8, 31)
  private static final String CVE_KNOWN_ISSUES_FILE_PATH = 'yarn-audit-known-issues-result'

  def securitytest

  // https://issues.jenkins.io/browse/JENKINS-47355 means a weird super class issue
  def localSteps

  YarnBuilder(steps) {
    super(steps)
    this.localSteps = steps
    this.securitytest = new SecurityScan(steps)
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

  def fullFunctionalTest() {
    try {
      yarn("test:fullfunctional")
    }
    finally {
      steps.junit allowEmptyResults: true, testResults: 'functional-output/**/*result.xml'
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def mutationTest() {
    try {
      yarn("test:mutation")
    }
    finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
    }
  }

  def securityCheck() {
    try {
      steps.sh """
        set +ex
        export NVM_DIR='/home/jenkinsssh/.nvm' # TODO get home from variable
        . /opt/nvm/nvm.sh || true
        nvm install
        set -ex
      """

      corepackEnable()
      steps.writeFile(file: 'yarn-audit-with-suppressions.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/yarn/yarn-audit-with-suppressions.sh'))
      steps.writeFile(file: 'prettyPrintAudit.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/yarn/prettyPrintAudit.sh'))
      steps.writeFile(file: 'format-v4-audit.cjs', text: steps.libraryResource('uk/gov/hmcts/pipeline/yarn/format-v4-audit.cjs'))

      steps.sh """
         export PATH=\$HOME/.local/bin:\$PATH
         export YARN_VERSION=\$(jq -r '.packageManager' package.json | sed 's/yarn@//' | grep -o '^[^.]*')
         chmod +x yarn-audit-with-suppressions.sh
        ./yarn-audit-with-suppressions.sh
      """
    } finally {
      steps.sh """
        cat yarn-audit-result-formatted | jq -c '. | {type: "auditSummary", data: .metadata}' > yarn-audit-issues-result-summary
        cat yarn-audit-result-formatted | jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' >> yarn-audit-issues-advisories
        cat yarn-audit-issues-result-summary yarn-audit-issues-advisories > yarn-audit-issues-result
      """
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

  @Override
  def techStackMaintenance() {
    this.steps.echo "Running Yarn Tech stack maintenance"
    try {
      def secrets = [
        [ secretType: 'Secret', name: 'ardoq-api-key', version: '', envVariable: 'ARDOQ_API_KEY' ],
        [ secretType: 'Secret', name: 'ardoq-api-url', version: '', envVariable: 'ARDOQ_API_URL' ]
      ]
      localSteps.withAzureKeyvault(secrets) {
        def client = new ArdoqClient(localSteps.env.ARDOQ_API_KEY, localSteps.env.ARDOQ_API_URL, steps)
        client.updateDependencies(localSteps.readFile('yarn.lock'), 'yarn')
      }
    } catch(Exception e) {
      localSteps.echo "Error running tech Yarn stack maintenance {e.getMessage()}"
    }
  }

  def prepareCVEReport(String issues, String knownIssues) {
    def jsonSlurper = new JsonSlurper()

    List<Object> issuesParsed = issues.split('\n').collect { jsonSlurper.parseText(it) }

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

  private runYarn(String task, String prepend = "") {
      if (prepend && !prepend.endsWith(' ')) {
        prepend += ' '
      }
  
      if (steps.fileExists(NVMRC)) {
        def status = steps.sh(script: """
          set +ex
          export NVM_DIR='/home/jenkinsssh/.nvm'
          . /opt/nvm/nvm.sh || true
          nvm install
          export PATH=\$HOME/.local/bin:\$PATH
  
          if ${prepend.toBoolean()}; then
            ${prepend}yarn ${task}
          else
            yarn ${task}
          fi
        """, returnStatus: true)
  
        if (status != 0 && !task.contains('install')) {
          steps.error("Yarn task '${task}' failed with status ${status}")
        }
      } else {
        def status = steps.sh(script: """
          export PATH=\$HOME/.local/bin:\$PATH
  
          if ${prepend.toBoolean()}; then
            ${prepend}yarn ${task}
          else
            yarn ${task}
          fi
        """, returnStatus: true)
  
        if (status != 0 && !task.contains('install')) {
          steps.error("Yarn task '${task}' failed with status ${status}")
        }
      }
  }

  private runYarnQuiet(String task, String prepend = "") {
    if (prepend && !prepend.endsWith(' ')) {
      prepend += ' '
    }
    def status = steps.sh(script: """
      export PATH=\$HOME/.local/bin:\$PATH

      if ${prepend.toBoolean()}; then
        ${prepend}yarn ${task} 1> /dev/null 2> /dev/null
      else
        yarn ${task} 1> /dev/null 2> /dev/null
      fi
    """, returnStatus: true)
    steps.echo("yarnQuiet ${task} -> ${status}")
    return status == 0  // only a 0 return status is success
  }

  private LocalDate node18ExpirationDate() {
    def date;
    switch (steps.env.PRODUCT) {
      case "xui":
      case "em":
        date = LocalDate.of(2024, 01, 15)
        break
      case "ccd":
        date = LocalDate.of(2023, 12, 21)
        break
      case "bar":
      case "fees-register":
      case "ccpay":
        date = LocalDate.of(2023, 12, 8)
        break
      default:
        date = NODEJS_EXPIRATION
        break
    }
    steps.echo "Node.Js upgrade deadline is: ${date}, product is: ${steps.env.PRODUCT}"
    return date
  }

  private isNodeJSV18OrNewer() {
    boolean validVersion = true;
    if (steps.fileExists(NVMRC)) {
      String nodeVersion = steps.readFile(NVMRC).replace("v", "")
      Float current_version = valueOf(nodeVersion
                                          .trim()
                                          .substring(0, nodeVersion.lastIndexOf(".")))
      validVersion = current_version >= DESIRED_MIN_VERSION
    } else {
      WarningCollector.addPipelineWarning("missing_nvrmc_file", "An nvrmc file is missing for this project. see https://github.com/hmcts/expressjs-template/blob/HEAD/.nvmrc", node18ExpirationDate())
    }

    return validVersion
  }

  private nagAboutOldNodeJSVersions() {
    if (!isNodeJSV18OrNewer()) {
      WarningCollector.addPipelineWarning("old_nodejs_version", "Please upgrade to NodeJS v18.16.0 or greater by updating the version in your .nvrmc file, https://nodejs.org/en", node18ExpirationDate())
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
    if (!steps.fileExists(INSTALL_CHECK_FILE)) {
      steps.sh("touch ${INSTALL_CHECK_FILE}")
      corepackEnable()
      runYarn("install")
    }
    runYarn(task, prepend)
  }

  @Override
  def securityScan(){
    if (steps.fileExists(".ci/security.sh")) {
      // hook to allow teams to override the default `security.sh` that we provide
      steps.writeFile(file: 'security.sh', text: steps.readFile('.ci/security.sh'))
    } else if (steps.fileExists("security.sh")) {
      WarningCollector.addPipelineWarning("security.sh_moved", "Please remove security.sh from root of repository, no longer needed as it has been moved to the Jenkins library", LocalDate.of(2023, 04, 17))
    } else {
      steps.writeFile(file: 'security.sh', text: steps.libraryResource('uk/gov/hmcts/pipeline/security/frontend/security.sh'))
    }
    this.securitytest.execute()
  }

  @Override
  def setupToolVersion() {
    super.setupToolVersion()
    // Java required on nodejs pipeline, if data import only available as java job, but project itself is nodejs
    def statusCodeJava21 = steps.sh(script: """
      find . -name "build.gradle" -exec grep -l "JavaLanguageVersion.of(21)" {} + > /dev/null
      """, returnStatus: true)    
    if (statusCodeJava21 == 0) {
      def javaHomeLocation = steps.sh(script: 'ls -d /usr/lib/jvm/temurin-21-jdk-*', returnStdout: true, label: 'Detect Java location').trim()
      steps.env.JAVA_HOME = javaHomeLocation
      steps.env.PATH = "${steps.env.JAVA_HOME}/bin:${steps.env.PATH}"
    }

    localSteps.sh "java -version"
    nagAboutOldNodeJSVersions()
  }

}
