package uk.gov.hmcts.contino
@Grab('com.microsoft.azure:azure-documentdb:1.15.2')

import com.cloudbees.groovy.cps.NonCPS
import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

class GradleBuilder extends AbstractBuilder {

  private static final String COSMOS_COLLECTION_LINK = 'dbs/jenkins/colls/cve-reports'

  def product
  String cosmosDbUrl
  def java11 = "11"

  GradleBuilder(steps, product) {
    super(steps)
    this.product = product
    Subscription subscription = new Subscription(steps.env)
    this.cosmosDbUrl = subscription.nonProdName == "sandbox" ?
      'https://sandbox-pipeline-metrics.documents.azure.com/' :
      'https://pipeline-metrics.documents.azure.com/'
  }

  def build() {
    addVersionInfo()
    gradle("assemble")
    steps.stash(name: product, includes: "**/libs/*.jar,**/libs/*.war")
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
      gradle("--info sonarqube")
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
    // this method is included in builder interface as part of nightly pipieline job
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
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v5-Account', version: '', envVariable: 'OWASPDB_V5_ACCOUNT' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v5-Password', version: '', envVariable: 'OWASPDB_V5_PASSWORD' ]
    ]
    steps.withAzureKeyvault(secrets) {
      try {
        gradle("-DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-v5-prod.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${steps.env.OWASPDB_V5_ACCOUNT}' -Ddata.password='${steps.env.OWASPDB_V5_PASSWORD}' -Dautoupdate='false' -Danalyzer.retirejs.enabled=false dependencyCheckAggregate")
      }
      finally {
        steps.archiveArtifacts 'build/reports/dependency-check-report.html'
        publishCVEReport()
      }
    }
  }

  def publishCVEReport() {
    try {
      steps.withCredentials([[$class: 'StringBinding', credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY']]) {
        if (steps.env.COSMOSDB_TOKEN_KEY == null) {
          steps.echo "Set the 'COSMOSDB_TOKEN_KEY' environment variable to enable metrics publishing"
          return
        }

        steps.echo "Publishing CVE report"
        String dependencyReport = steps.readFile('build/reports/dependency-check-report.json')
        def summary = prepareCVEReport(dependencyReport, steps.env)
        createDocument(summary)
      }
    } catch (err) {
      steps.echo "Unable to publish CVE report '${err}'"
    }
  }

  def prepareCVEReport(owaspReportJSON, env) {
    def report = new JsonSlurper().parseText(owaspReportJSON)
    // Only include vulnerable dependencies to reduce the report size; Cosmos has a 2MB limit.
    report.dependencies = report.dependencies.findAll {
      it.vulnerabilities || it.suppressedVulnerabilities
    }

    def result = [
      build: [
        branch_name                  : env.BRANCH_NAME,
        build_display_name           : env.BUILD_DISPLAY_NAME,
        build_tag                    : env.BUILD_TAG,
        git_url                      : env.GIT_URL,
      ],
      report: report
    ]
    return JsonOutput.toJson(result)
  }

  @NonCPS
  private def createDocument(String reportJSON) {
    def client = new DocumentClient(cosmosDbUrl, steps.env.COSMOSDB_TOKEN_KEY, null, null)
    try {
      client.createDocument(COSMOS_COLLECTION_LINK, new Document(reportJSON)
        , null, false)
    } finally {
      client.close()
    }
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
    gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.provider.version=${version} -Dpact.verifier.publishResults=${publish} runProviderPactVerification")
  }

  def runConsumerTests(pactBrokerUrl, version) {
    gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.consumer.version=${version} runAndPublishConsumerPactTests")
  }

  def gradle(String task) {
    addInitScript()
    steps.sh("./gradlew --no-daemon --init-script init.gradle ${task}")
  }

  private String gradleWithOutput(String task) {
    addInitScript()
    steps.sh(script: "./gradlew --no-daemon --init-script init.gradle ${task}", returnStdout: true).trim()
  }

  def fullFunctionalTest() {
      functionalTest()
  }

  def dbMigrate(String vaultName, String microserviceName) {
    def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }

    def dbName = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-DATABASE' --query value -o tsv"
    def dbHost = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-HOST' --query value -o tsv"
    def dbPass = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PASS' --query value -o tsv"
    def dbPort = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PORT' --query value -o tsv"
    def dbUser = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-USER' --query value -o tsv"

    gradle("-Pdburl='${dbHost}:${dbPort}/${dbName}?ssl=true&sslmode=require' -Pflyway.user='${dbUser}' -Pflyway.password='${dbPass}' migratePostgresDatabase")
  }

  @Override
  def setupToolVersion() {
    gradle("--version") // ensure wrapper has been downloaded
    try {
      def javaVersion = gradleWithOutput("-q :javaVersion")
      steps.echo "Found java version: ${javaVersion}"
      if (javaVersion == java11) {
        steps.env.JAVA_HOME = "/usr/share/jdk-11.0.2"
        steps.env.PATH = "${steps.env.JAVA_HOME}/bin:${steps.env.PATH}"
      } else {
        nagAboutJava11Required()
      }
    } catch(err) {
      steps.echo "Failed to detect java version, ensure the root project has sourceCompatibility set"
      nagAboutJava11Required()
    }
    steps.sh "java -version"
  }

  def nagAboutJava11Required() {
    WarningCollector.addPipelineWarning("deprecate_java_8", "Java 11 is required for all projects, change your source compatibility to 11 and update your Dockerfile base, see https://github.com/hmcts/draft-store/pull/644. ", new Date().parse("dd.MM.yyyy", "19.08.2020"))
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
