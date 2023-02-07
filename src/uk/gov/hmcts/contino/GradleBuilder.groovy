package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import hudson.Functions
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
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v6-Account', version: '', envVariable: 'OWASPDB_V6_ACCOUNT' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v6-Password', version: '', envVariable: 'OWASPDB_V6_PASSWORD' ]
    ]
    localSteps.azureKeyVault(secrets) {
      try {
        // using sh directly so that secrets don't get interpolated in the gradle function
        steps.sh(
          './gradlew --no-daemon --init-script init.gradle --stacktrace -DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name="org.postgresql.Driver" -Ddata.connection_string="jdbc:postgresql://owaspdependency-v6-prod.postgres.database.azure.com/owaspdependencycheck" -Ddata.user=$OWASPDB_V6_ACCOUNT -Ddata.password=$OWASPDB_V6_PASSWORD -Danalyzer.retirejs.enabled=false -Danalyzer.ossindex.enabled=false dependencyCheckAggregate'
        )
      } catch (Exception e){
        Functions.printThrowable(e)
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
    gradle("--version") // ensure wrapper has been downloaded
    localSteps.sh "java -version"
  }

  def hasPlugin(String pluginName) {
    return gradleWithOutput("buildEnvironment").contains(pluginName)
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
