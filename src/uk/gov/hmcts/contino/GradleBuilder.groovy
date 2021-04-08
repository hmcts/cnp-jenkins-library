package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

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
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v6-Account', version: '', envVariable: 'OWASPDB_V6_ACCOUNT' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v5-Password', version: '', envVariable: 'OWASPDB_V5_PASSWORD' ],
      [ secretType: 'Secret', name: 'OWASPPostgresDb-v6-Password', version: '', envVariable: 'OWASPDB_V6_PASSWORD' ]
    ]
    steps.withAzureKeyvault(secrets) {
      try {
        if (hasPlugin("org.owasp:dependency-check-gradle:6")) {
          gradle("-DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-v6-prod.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${steps.env.OWASPDB_V6_ACCOUNT}' -Ddata.password='${steps.env.OWASPDB_V6_PASSWORD}' -Dautoupdate='false' -Danalyzer.retirejs.enabled=false dependencyCheckAggregate")
        } else {
          WarningCollector.addPipelineWarning("deprecate_owasp_5", "Older versions of Owasp dependency check  plugin are not supported, please move to latest 6.x. For example see https://github.com/hmcts/service-auth-provider-app/pull/322 ", new Date().parse("dd.MM.yyyy", "15.10.2020"))
          gradle("-DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-v5-prod.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${steps.env.OWASPDB_V5_ACCOUNT}' -Ddata.password='${steps.env.OWASPDB_V5_PASSWORD}' -Dautoupdate='false' -Danalyzer.retirejs.enabled=false dependencyCheckAggregate")
        }
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
    gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.provider.version=${version} -Dpact.verifier.publishResults=${publish} runProviderPactVerification")
  }

  def runConsumerTests(pactBrokerUrl, version) {
    gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.consumer.version=${version} runAndPublishConsumerPactTests")
  }

  def runConsumerCanIDeploy() {
    gradle("canideploy")
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

