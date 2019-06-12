package uk.gov.hmcts.contino

class GradleBuilder extends AbstractBuilder {

  def product

  def java11 = "11"

  GradleBuilder(steps, product) {
    super(steps)
    this.product = product
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
      gradle("--info check")
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
      gradle("--info --rerun-tasks smoke")
    } finally {
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def functionalTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--info --rerun-tasks functional")
    } finally {
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def apiGatewayTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      gradle("--info --rerun-tasks apiGateway")
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
    if (hasPlugin("org.owasp.dependencycheck.gradle.plugin:5")) {
      def secrets = [
        [ secretType: 'Secret', name: 'OWASPPostgresDb-v5-Account', version: '', envVariable: 'OWASPDB_V5_ACCOUNT' ],
        [ secretType: 'Secret', name: 'OWASPPostgresDb-v5-Password', version: '', envVariable: 'OWASPDB_V5_PASSWORD' ]
      ]
      steps.withAzureKeyVault(secrets) {
        try {
          gradle("-DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-v5-prod.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${steps.env.OWASPDB_ACCOUNT}' -Ddata.password='${steps.env.OWASPDB_PASSWORD}' -Dautoupdate='false' dependencyCheckAnalyze")
        }
        finally {
          steps.archiveArtifacts 'build/reports/dependency-check-report.html'
        }
      }
    } else {
      steps.withCredentials([steps.usernamePassword(credentialsId: 'owasp-postgresdb-login', usernameVariable: 'OWASPDB_ACCOUNT', passwordVariable: 'OWASPDB_PASSWORD')]) {
        try {
          gradle("-DdependencyCheck.failBuild=true -Dcve.check.validforhours=24 -Danalyzer.central.enabled=false -Ddata.driver_name='org.postgresql.Driver' -Ddata.connection_string='jdbc:postgresql://owaspdependency-prod.postgres.database.azure.com/owaspdependencycheck' -Ddata.user='${steps.env.OWASPDB_ACCOUNT}' -Ddata.password='${steps.env.OWASPDB_PASSWORD}' -Dautoupdate='false' dependencyCheckAnalyze")
        }
        finally {
          steps.archiveArtifacts 'build/reports/dependency-check-report.html'
        }
      }
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

  def runProviderVerification(pactBrokerUrl, version) {
    gradle("-Dpact.broker.url=${pactBrokerUrl} -Dpact.provider.version=${version} -Dpact.verifier.publishResults=true runAndPublishProviderPactVerification")
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
      }
    } catch(err) {
      steps.echo "Failed to detect java version, ensure the root project has sourceCompatibility set"
    }
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
