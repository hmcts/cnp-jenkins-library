package uk.gov.hmcts.contino

import jenkins.model.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.impl.*

class GradleBuilder implements Builder, Serializable {

  def steps
  def product

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
      def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }

      def owaspU = az "keyvault secret show --vault-name '${steps.env.INFRA_VAULT_NAME}' --name 'OWASPDb-Account' --query value -o tsv"
      def owaspP = az "keyvault secret show --vault-name '${steps.env.INFRA_VAULT_NAME}' --name 'OWASPDb-Password' --query value -o tsv"

      //trying to get a handle on Jenkins global system credentials provider to inject our creds
      def domain = Domain.global()
      def store = SystemCredentialsProvider.getInstance().getStore()

      def credential = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "owaspCredentials", "DB credentials for OWASP DB", "${owaspU}", "${owaspP}")
      def success = store.addCredentials(domain, credential)

      if (success) {
        log.info "owaspCredentials created successfully"
        withCredentials([UsernamePasswordMultiBinding(credentialsId: 'owaspCredentials', usernameVariable: 'OWASP_USER', passwordVariable: 'OWASP_PASS') ]) {
          gradle("-DdependencyCheck.failBuild=true -DdependencyCheck.cveValidForHours=24 -DdependencyCheck.data.driver='com.microsoft.sqlserver.jdbc.SQLServerDriver' -DdependencyCheck.data.connectionString='jdbc:sqlserver://owaspdependencycheck.database.windows.net:1433;database=owaspdependencycheck;user=${OWASP_USER}@owaspdependencycheck;password=${OWASP_PASS};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;' -DdependencyCheck.data.username='${OWASP_USER}' -DdependencyCheck.data.password='${OWASP_PASS}' dependencyCheck")
        }
        store.removeCredentials(domain, credential)
      } else {
        log.info "something went wrong creating owaspCredentials on Jenkins"
        withEnv(["OWASP_USER=$owaspU",
                 "OWASP_PASS=$owaspP"]) {
          gradle("-DdependencyCheck.failBuild=true -DdependencyCheck.cveValidForHours=24 -DdependencyCheck.data.driver='com.microsoft.sqlserver.jdbc.SQLServerDriver' -DdependencyCheck.data.connectionString='jdbc:sqlserver://owaspdependencycheck.database.windows.net:1433;database=owaspdependencycheck;user=${OWASP_USER}@owaspdependencycheck;password=${OWASP_PASS};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;' -DdependencyCheck.data.username='${OWASP_USER}' -DdependencyCheck.data.password='${OWASP_PASS}' dependencyCheck")
        }
      }

    }
    finally {
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
    def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }

    def dbName = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-DATABASE' --query value -o tsv"
    def dbHost = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-HOST' --query value -o tsv"
    def dbPass = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PASS' --query value -o tsv"
    def dbPort = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PORT' --query value -o tsv"
    def dbUser = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-USER' --query value -o tsv"

    gradle("-Pdburl='${dbHost}:${dbPort}/${dbName}?ssl=true' -Pflyway.user='${dbUser}' -Pflyway.password='${dbPass}' migratePostgresDatabase")
  }

}
