package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class GradleBuilder extends AbstractBuilder {

  def nonProdName = System.getenv("NONPROD_SUBSCRIPTION_NAME")

  def owaspenv = nonProdName

  def product

  // https://issues.jenkins.io/browse/JENKINS-47355 means a weird super class issue
  def localSteps

  def securitytest

  GradleBuilder(steps, product) {
    super(steps)
    this.product = product
    this.localSteps = steps
    this.securitytest = new SecurityScan(this.steps)
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
      try {
        localSteps.junit '**/test-results/smoke/*.xml,**/test-results/smokeTest/*.xml'
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_smoke_test_archiving", "No smoke  test results found, make sure you have at least one created.", LocalDate.of(2022, 6, 30))
      }
    }
  }

  def functionalTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks functional")
    } finally {
      try {
        localSteps.junit '**/test-results/functional/*.xml,**/test-results/functionalTest/*.xml'
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_functional_test_archiving", "No functional test results found, make sure you have at least one created.", LocalDate.of(2022, 6, 30))
      }
    }
  }

  def apiGatewayTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks apiGateway")
    } finally {
      try {
        localSteps.junit '**/test-results/api/*.xml,**/test-results/apiTest/*.xml'
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_apiGateway_test_archiving", "No API gateway test results found, make sure you have at least one created.", LocalDate.of(2022, 6, 30))
      }
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
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v14-Account', version: '', envVariable: 'OWASPDB_V14_ACCOUNT' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v14-Password', version: '', envVariable: 'OWASPDB_V14_PASSWORD' ]
    ]
    localSteps.withAzureKeyvault(secrets) {
      try {
          gradle("--stacktrace -DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-v14-flexible-${owaspenv}.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${localSteps.env.OWASPDB_V14_ACCOUNT}' -Ddata.password='${localSteps.env.OWASPDB_V14_PASSWORD}'  -Danalyzer.retirejs.enabled=false -Danalyzer.ossindex.enabled=false dependencyCheckAggregate")
      } finally {
        localSteps.archiveArtifacts 'build/reports/dependency-check-report.html'
        String dependencyReport = localSteps.readFile('build/reports/dependency-check-report.json')

        def cveReport = prepareCVEReport(dependencyReport)

        new CVEPublisher(localSteps)
          .publishCVEReport('java', cveReport)
      }
    }
  }

  def prepareCVEReport(String owaspReportJSON) {
    def report = new JsonSlurper().parseText(owaspReportJSON)
    // Only include vulnerable dependencies to reduce the report size; Cosmos has a 2MB limit.
    report.dependencies = report.dependencies.findAll {
      it.vulnerabilities || it.suppressedVulnerabilities
    }

    return report
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
      gradle("-Ppact.broker.url=${pactBrokerUrl} -Ppact.provider.version=${version} -Ppact.verifier.publishResults=${publish} runProviderPactVerification")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: '**/test-results/contract/TEST-*.xml,**/test-results/contractTest/TEST-*.xml'
    }
  }

  def runConsumerTests(pactBrokerUrl, version) {
   try {
      gradle("-Ppact.broker.url=${pactBrokerUrl} -Ppact.consumer.version=${version} runAndPublishConsumerPactTests")
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
    try {
      def statusCode = steps.sh script: 'grep -F "JavaLanguageVersion.of(17)" build.gradle', returnStatus: true
      if (statusCode == 0) {
        steps.env.JAVA_HOME = "/usr/lib/jvm/java-17-openjdk-amd64"
        steps.env.PATH = "${steps.env.JAVA_HOME}/bin:${steps.env.PATH}"
        // Workaround jacocoTestReport issue https://github.com/gradle/gradle/issues/18508#issuecomment-1049998305
        steps.env.GRADLE_OPTS = "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED"
      } else {
        WarningCollector.addPipelineWarning("java_11_deprecated",
          "Please upgrade to Java 17, upgrade to " +
            "<https://moj.enterprise.slack.com/files/T02DYEB3A/F02V9BNFXRU?origin_team=T1L0WSW9F|Application Insights v3 first>, " +
            "then <https://github.com/hmcts/draft-store/pull/989|upgrade to Java 17>. " +
            "Make sure you use the latest version of the Application insights agent, see the configuration in " +
            "<https://github.com/hmcts/spring-boot-template/|spring-boot-template>, " +
            "look at the `.github/renovate.json` and `Dockerfile` files.", LocalDate.of(2023, 8, 1)
        )
      }
    } catch(err) {
      steps.echo "Failed to detect java version, ensure the root project has the correct Java requirements set"
    }

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
    } else {
      localSteps.writeFile(file: 'security.sh', text: localSteps.libraryResource('uk/gov/hmcts/pipeline/security/backend/security.sh'))
    }
    this.securitytest.execute()
  }

  @Override
  def performanceTest() {
    //support for the new and old (deprecated) gatling gradle plugins
    if (hasPlugin("gatling-gradle-plugin") || hasPlugin("gradle-gatling-plugin")) {
      localSteps.env.GATLING_REPORTS_PATH = 'build/reports/gatling'
      localSteps.env.GATLING_REPORTS_DIR =  '$WORKSPACE/' + localSteps.env.GATLING_REPORTS_PATH
      gradle("gatlingRun")
      this.localSteps.gatlingArchive()
    } else {
      super.executeGatling()
    }
  }
}
