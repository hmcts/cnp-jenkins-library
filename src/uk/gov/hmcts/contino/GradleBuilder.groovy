package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.ardoq.ArdoqClient
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class GradleBuilder extends AbstractBuilder {

  def product

  // https://issues.jenkins.io/browse/JENKINS-47355 means a weird super class issue
  def localSteps

  GradleBuilder(steps, product) {
    super(steps)
    this.product = product
    this.localSteps = steps
  }

  def build() {
    addVersionInfo()
    gradle("assemble")
  }

  def fortifyScan() {
    gradle("fortifyScan")
  }

  def addInitScript() {
    localSteps.writeFile(file: 'init.gradle', text: localSteps.libraryResource('uk/gov/hmcts/gradle/init.gradle'))
  }

  def test() {
    try {
      gradle("check")
    } finally {
      localSteps.junit '**/test-results/test/*.xml'
      localSteps.archiveArtifacts artifacts: '**/reports/checkstyle/*.html', allowEmptyArchive: true
    }
  }

  def sonarScan() {
      String properties = SonarProperties.get(localSteps)

      gradle("--info ${properties} sonarqube")
  }

  def highLevelDataSetup(String dataSetupEnvironment) {
    gradle("highLevelDataSetup --args=${dataSetupEnvironment}")
  }

  def smokeTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks smoke")
    } finally {
      localSteps.junit '**/test-results/smoke/*.xml,**/test-results/smokeTest/*.xml'
    }
  }

  def functionalTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks functional")
    } finally {
      if (product == "civil") {
        localSteps.junit allowEmptyResults: true, testResults: '**/test-results/functional/*.xml,**/test-results/functionalTest/*.xml'
      } else {
        localSteps.junit '**/test-results/functional/*.xml,**/test-results/functionalTest/*.xml'
      }
    }
  }

  def apiGatewayTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks apiGateway")
    } finally {
      localSteps.junit '**/test-results/api/*.xml,**/test-results/apiTest/*.xml'
    }
  }

  def crossBrowserTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      localSteps.withSauceConnect("reform_tunnel") {
        gradle("--rerun-tasks crossbrowser")
      }
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
      localSteps.saucePublisher()
    }
  }

  def crossBrowserTest(String browser) {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      localSteps.withSauceConnect("reform_tunnel") {
        gradle("--rerun-tasks crossbrowser", "BROWSER_GROUP=$browser")
      }
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
      localSteps.saucePublisher()
    }
  }

  def mutationTest(){
    try {
      gradle("pitest")
    }
    finally {
      localSteps.archiveArtifacts '**/reports/pitest/**/*.*'
    }
  }

  def securityCheck() {
    def secrets = [
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v15-Account', version: '', envVariable: 'OWASPDB_V15_ACCOUNT' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v15-Password', version: '', envVariable: 'OWASPDB_V15_PASSWORD' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v15-Connection-String', version: '', envVariable: 'OWASPDB_V15_CONNECTION_STRING' ]
    ]

    localSteps.withAzureKeyvault(secrets) {
      try {
        gradle("--stacktrace -DdependencyCheck.failBuild=true -Dnvd.api.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='${localSteps.env.OWASPDB_V15_CONNECTION_STRING}' -Ddata.user='${localSteps.env.OWASPDB_V15_ACCOUNT}' -Ddata.password='${localSteps.env.OWASPDB_V15_PASSWORD}'  -Danalyzer.retirejs.enabled=false -Danalyzer.ossindex.enabled=false dependencyCheckAggregate")
      } finally {
        localSteps.archiveArtifacts 'build/reports/dependency-check-report.html'
        String dependencyReport = localSteps.readFile('build/reports/dependency-check-report.json')

        def cveReport = prepareCVEReport(dependencyReport)

        new CVEPublisher(localSteps)
          .publishCVEReport('java', cveReport)
      }
    }
  }

  @Override
  def techStackMaintenance() {
    localSteps.echo "Running Gradle Tech stack maintenance"
    try {
      def secrets = [
        [ secretType: 'Secret', name: 'ardoq-api-key', version: '', envVariable: 'ARDOQ_API_KEY' ],
        [ secretType: 'Secret', name: 'ardoq-api-url', version: '', envVariable: 'ARDOQ_API_URL' ]
      ]
      localSteps.withAzureKeyvault(secrets) {
        localSteps.sh "./gradlew -q dependencies > depsProc"
        def client = new ArdoqClient(localSteps.env.ARDOQ_API_KEY, localSteps.env.ARDOQ_API_URL, steps)
        client.updateDependencies(localSteps.readFile('depsProc'), 'gradle')
      }
    } catch(Exception e) {
      localSteps.echo "Error running Gradle tech stack maintenance {e.getMessage()}"
    }
  }

  def prepareCVEReport(String owaspReportJSON) {
    if (!owaspReportJSON || owaspReportJSON.trim().isEmpty()) {
      // Return empty report structure if no JSON provided (common in test environments)
      return [dependencies: []]
    }

    try {
      def report = new JsonSlurperClassic().parseText(owaspReportJSON)
      // Only include vulnerable dependencies to reduce the report size; Cosmos has a 2MB limit.
      report.dependencies = report.dependencies.findAll {
        it.vulnerabilities || it.suppressedVulnerabilities
      }
      return report
    } catch (Exception e) {
      // If JSON parsing fails, return empty report structure
      return [dependencies: []]
    }
  }

  @Override
  def addVersionInfo() {
    addInitScript()
    localSteps.sh '''
mkdir -p src/main/resources/META-INF

tee src/main/resources/META-INF/build-info.properties <<EOF 2>/dev/null
build.version=$(./gradlew --no-daemon --init-script init.gradle -q :printVersionInit)
build.number=${BUILD_NUMBER}
build.commit=$(git rev-parse HEAD)
build.date=$(date)
EOF

'''
  }

  def runProviderVerification(pactBrokerUrl, version, publish) {
    try {
      gradle("-Ppact.broker.url=${pactBrokerUrl} -Ppactbroker.url=${pactBrokerUrl} -Ppact.provider.version=${version} -Ppact.verifier.publishResults=${publish} runProviderPactVerification")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: '**/test-results/contract/TEST-*.xml,**/test-results/contractTest/TEST-*.xml'
    }
  }

  def runConsumerTests(pactBrokerUrl, version) {
   try {
      gradle("-Ppact.broker.url=${pactBrokerUrl} -Ppactbroker.url=${pactBrokerUrl} -Ppact.consumer.version=${version} runAndPublishConsumerPactTests")
   } finally {
      localSteps.junit allowEmptyResults: true, testResults: '**/test-results/contract/TEST-*.xml,**/test-results/contractTest/TEST-*.xml'
    }
  }

  def runConsumerCanIDeploy() {
    try {
      gradle("canideploy")
     } finally {
      localSteps.junit allowEmptyResults: true, testResults: '**/test-results/contract/TEST-*.xml,**/test-results/contractTest/TEST-*.xml'
    }
  }


  def gradle(String task, String prepend = "") {
    if (prepend && !prepend.endsWith(' ')) {
      prepend += ' '
    }
    addInitScript()
    localSteps.sh("${prepend}./gradlew --no-daemon --init-script init.gradle ${task}")
  }

  private String gradleWithOutput(String task) {
    addInitScript()
    localSteps.sh(script: "./gradlew --no-daemon --init-script init.gradle ${task}", returnStdout: true).trim()
  }

  def fullFunctionalTest() {
      functionalTest()
  }

  def dbMigrate(String vaultName, String microserviceName) {
    def secrets = [
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-DATABASE", version: '', envVariable: 'POSTGRES_DATABASE' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-HOST", version: '', envVariable: 'POSTGRES_HOST' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-PASS", version: '', envVariable: 'POSTGRES_PASS' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-PORT", version: '', envVariable: 'POSTGRES_PORT' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-USER", version: '', envVariable: 'POSTGRES_USER' ]
    ]

    def azureKeyVaultURL = "https://${vaultName}.vault.azure.net"

    localSteps.azureKeyVault(secrets: secrets, keyVaultURL: azureKeyVaultURL) {
      gradle("-Pdburl='${localSteps.env.POSTGRES_HOST}:${localSteps.env.POSTGRES_PORT}/${localSteps.env.POSTGRES_DATABASE}?ssl=true&sslmode=require' -Pflyway.user='${localSteps.env.POSTGRES_USER}' -Pflyway.password='${localSteps.env.POSTGRES_PASS}' migratePostgresDatabase")
    }
  }

  @Override
  def setupToolVersion() {
    def statusCode = steps.sh script: 'grep -F "JavaLanguageVersion.of(11)" build.gradle', returnStatus: true
    if (statusCode == 0) {
      WarningCollector.addPipelineWarning("java_11_deprecated",
        "Please upgrade to Java 17, upgrade to " +
          "<https://moj.enterprise.slack.com/files/T02DYEB3A/F02V9BNFXRU?origin_team=T1L0WSW9F|Application Insights v3 first>, " +
          "then <https://github.com/hmcts/draft-store/pull/989|upgrade to Java 17>. " +
          "Make sure you use the latest version of the Application insights agent, see the configuration in " +
          "<https://github.com/hmcts/spring-boot-template/|spring-boot-template>, " +
          "look at the `.github/renovate.json` and `Dockerfile` files.", LocalDate.of(2023, 8, 1)
      )
    }

    def statusCodeJava21 = steps.sh script: 'grep -F "JavaLanguageVersion.of(21)" build.gradle', returnStatus: true
    if (statusCodeJava21 == 0) {
      def javaHomeLocation = steps.sh(script: 'ls -d /usr/lib/jvm/temurin-21-jdk-*', returnStdout: true, label: 'Detect Java location').trim()
      steps.env.JAVA_HOME = javaHomeLocation
      steps.env.PATH = "${steps.env.JAVA_HOME}/bin:${steps.env.PATH}"
    }

    // Workaround jacocoTestReport issue https://github.com/gradle/gradle/issues/18508#issuecomment-1049998305
    steps.env.GRADLE_OPTS = "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED"
    gradle("--version") // ensure wrapper has been downloaded
    localSteps.sh "java -version"
  }

  def hasPlugin(String pluginName) {
    return gradleWithOutput("buildEnvironment").contains(pluginName)
  }

  @Override
  def securityScan(){
    if (steps.fileExists(".ci/security.sh")) {
      // hook to allow teams to override the default `security.sh` that we provide
      steps.writeFile(file: 'security.sh', text: steps.readFile('.ci/security.sh'))
    } else if (localSteps.fileExists("security.sh")) {
      WarningCollector.addPipelineWarning("security.sh_moved", "Please remove security.sh from root of repository, no longer needed as it has been moved to the Jenkins library", LocalDate.of(2023, 04, 17))
    } else if (localSteps.env.SCAN_TYPE == "frontend") {
      localSteps.writeFile(file: 'security.sh', text: localSteps.libraryResource('uk/gov/hmcts/pipeline/security/frontend/security.sh'))
    } else {
      localSteps.writeFile(file: 'security.sh', text: localSteps.libraryResource('uk/gov/hmcts/pipeline/security/backend/security.sh'))
    }
    this.securitytest.execute()
  }

  @Override
  def performanceTest(simulation = null) {
    //support for the new and old (deprecated) gatling gradle plugins
    if (hasPlugin("gatling-gradle-plugin") || hasPlugin("gradle-gatling-plugin")) {
      localSteps.env.GATLING_REPORTS_PATH = 'build/reports/gatling'
      localSteps.env.GATLING_REPORTS_DIR =  '$WORKSPACE/' + localSteps.env.GATLING_REPORTS_PATH
      
      def gatlingCommand = simulation ? "gatlingRun --simulation=${simulation}" : "gatlingRun"
      gradle(gatlingCommand)
      this.localSteps.gatlingArchive()
    } else {
      WarningCollector.addPipelineWarning("gatling_docker_deprecated",
        "Please use the gatling plugin instead of the docker image " +
          "See <https://github.com/hmcts/cnp-plum-recipes-service/pull/817/files|example>", LocalDate.of(2023, 9, 1)
      )
      super.executeGatling()
    }
  }
}
