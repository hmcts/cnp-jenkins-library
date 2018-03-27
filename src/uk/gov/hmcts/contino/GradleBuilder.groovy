package uk.gov.hmcts.contino

class GradleBuilder implements Builder, Serializable {

  def steps
  def product
  def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }

  GradleBuilder(steps, product) {
    this.steps = steps
    this.product = product
  }

  def build() {
    addVersionInfo()
    gradle("assemble")
    steps.stash(name: product, includes: "**/libs/*.jar")
  }

  def test() {
    try {
      gradle("--info check")
    } finally {
      steps.junit '**/test-results/**/*.xml'
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

  def securityCheck() {

    try {
       def owaspUser = az "keyvault secret show --vault-name 'https://infra-vault.vault.azure.net/' --name 'OWASPDb-Account' --query value -o tsv"
       def owaspPassword = az "keyvault secret show --vault-name 'https://infra-vault.vault.azure.net/' --name 'OWASPDb-Password' --query value -o tsv"      
       gradle("-DdependencyCheck.failBuild=true -DdependencyCheck.cveValidForHours=24 -DdependencyCheck.data.driver='com.microsoft.sqlserver.jdbc.SQLServerDriver' -DdependencyCheck.data.connectionString='jdbc:sqlserver://owaspdependencycheck.database.windows.net:1433;database=owaspdependencycheck;user=${owaspUser}@owaspdependencycheck;password=${owaspPassword};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;' -DdependencyCheck.data.username='${owaspUser}' -DdependencyCheck.data.password='${owaspPassword}' dependencyCheck")
     } finally {
       steps.archiveArtifacts 'build/reports/dependency-check-report.html'
     }

  }

  @Override
  def addVersionInfo() {
    steps.sh '''
mkdir -p src/main/resources/META-INF
echo "allprojects { task printVersionInit { doLast { println project.version } } }" > init.gradle

tee src/main/resources/META-INF/build-info.properties <<EOF 2>/dev/null
build.version=$(./gradlew --init-script init.gradle -q :printVersionInit)
build.number=${BUILD_NUMBER}
build.commit=$(git rev-parse HEAD)
build.date=$(date)
EOF

'''
  }

  def gradle(String task) {
    steps.sh("./gradlew ${task}")
  }

  def dbMigrate(String vaultName, String microserviceName) {

    def dbName = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-DATABASE' --query value -o tsv"
    def dbHost = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-HOST' --query value -o tsv"
    def dbPass = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PASS' --query value -o tsv"
    def dbPort = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PORT' --query value -o tsv"
    def dbUser = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-USER' --query value -o tsv"

    gradle("-Pdburl='${dbHost}:${dbPort}/${dbName}?ssl=true' -Pflyway.user='${dbUser}' -Pflyway.password='${dbPass}' migratePostgresDatabase")
  }

}
