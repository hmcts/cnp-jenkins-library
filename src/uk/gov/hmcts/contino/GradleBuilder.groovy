package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties

class GradleBuilder extends AbstractBuilder {

  def product

  GradleBuilder(steps, product) {
    super(steps)
    this.product = product
  }

  def build() {
    addVersionInfo()
    gradle("assemble")
  }

  def fortifyScan() {
    gradle("fortifyScan")
  }

  def addInitScript() {
    steps.writeFile(file: 'init.gradle', text: steps.libraryResource('uk/gov/hmcts/gradle/init.gradle'))
  }

  def test() {
    try {
      gradle("check")
    } finally {
      steps.junit '**/test-results/**/*.xml'
      steps.archiveArtifacts artifacts: '**/reports/checkstyle/*.html', allowEmptyArchive: true
    }
  }

  def sonarScan() {
      String properties = SonarProperties.get(steps)

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
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def functionalTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks functional")
    } finally {
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def apiGatewayTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--rerun-tasks apiGateway")
    } finally {
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def crossBrowserTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      steps.withSauceConnect("reform_tunnel") {
        gradle("--rerun-tasks crossbrowser")
      }
    } finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
      steps.saucePublisher()
    }
  }

  def crossBrowserTest(String browser) {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      steps.withSauceConnect("reform_tunnel") {
        gradle("--rerun-tasks crossbrowser", "BROWSER_GROUP=$browser")
      }
    } finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
      steps.saucePublisher()
    }
  }

  def mutationTest(){
    try {
      gradle("pitest")
    }
    finally {
      steps.archiveArtifacts '**/reports/pitest/**/*.*'
    }
  }

  def securityCheck() {
    def secrets = [
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v6-Account', version: '', envVariable: 'OWASPDB_V6_ACCOUNT' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v6-Password', version: '', envVariable: 'OWASPDB_V6_PASSWORD' ]
    ]
    steps.withAzureKeyvault(secrets) {
      try {
          gradle("--stacktrace -DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-v6-prod.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${steps.env.OWASPDB_V6_ACCOUNT}' -Ddata.password='${steps.env.OWASPDB_V6_PASSWORD}'  -Danalyzer.retirejs.enabled=false dependencyCheckAggregate")
      }
      finally {
        steps.archiveArtifacts 'build/reports/dependency-check-report.html'
        String dependencyReport = steps.readFile('build/reports/dependency-check-report.json')

        def cveReport = prepareCVEReport(dependencyReport)

        CVEPublisher.create(steps)
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
    steps.sh '''
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
      gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.provider.version=${version} -Dpact.verifier.publishResults=${publish} runProviderPactVerification")
    } finally {
      steps.junit allowEmptyResults: true, testResults: '**/test-results/contract/TEST-*.xml,**/test-results/contractTest/TEST-*.xml'
    }
  }

  def runConsumerTests(pactBrokerUrl, version) {
    gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.consumer.version=${version} runAndPublishConsumerPactTests")
  }

  def runConsumerCanIDeploy() {
    gradle("canideploy")
  }


  def gradle(String task, String prepend = "") {
    if (prepend && !prepend.endsWith(' ')) {
      prepend += ' '
    }
    addInitScript()
    steps.sh("${prepend}./gradlew --no-daemon --init-script init.gradle ${task}")
  }

  private String gradleWithOutput(String task) {
    addInitScript()
    steps.sh(script: "./gradlew --no-daemon --init-script init.gradle ${task}", returnStdout: true).trim()
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

    steps.azureKeyVault(secrets: secrets, keyVaultURL: azureKeyVaultURL) {
      gradle("-Pdburl='${steps.env.POSTGRES_HOST}:${steps.env.POSTGRES_PORT}/${steps.env.POSTGRES_DATABASE}?ssl=true&sslmode=require' -Pflyway.user='${steps.env.POSTGRES_USER}' -Pflyway.password='${steps.env.POSTGRES_PASS}' migratePostgresDatabase")
    }
  }

  @Override
  def setupToolVersion() {
    gradle("--version") // ensure wrapper has been downloaded
    steps.sh "java -version"
  }

  def hasPlugin(String pluginName) {
    return gradleWithOutput("buildEnvironment").contains(pluginName)
  }

  @Override
  def performanceTest() {
    if (hasPlugin("gradle-gatling-plugin")) {
      steps.env.GATLING_REPORTS_PATH = 'build/reports/gatling'
      steps.env.GATLING_REPORTS_DIR =  '$WORKSPACE/' + steps.env.GATLING_REPORTS_PATH
      gradle("gatlingRun")
      this.steps.gatlingArchive()
    } else {
      super.executeGatling()
    }
  }

}
