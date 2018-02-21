package uk.gov.hmcts.contino

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
      gradle("--info smoke")
    } finally {
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def functionalTest() {
    try {
      gradle("--info functional")
    } finally {
      steps.junit '**/test-results/**/*.xml'
    }
  }

  def securityCheck() {

    // try {
    //   gradle("-DdependencyCheck.failBuild=true dependencyCheck")
    // } finally {
    //   steps.archiveArtifacts 'build/reports/dependency-check-report.html'
    // }

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
    def dbName = steps.sh(script: "az keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-DATABASE' --query value -o tsv", returnStdout: true).trim()
    def dbHost = steps.sh(script: "az keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-HOST' --query value -o tsv", returnStdout: true).trim()
    def dbPass = steps.sh(script: "az keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PASS' --query value -o tsv", returnStdout: true).trim()
    def dbPort = steps.sh(script: "az keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PORT' --query value -o tsv", returnStdout: true).trim()
    def dbUser = steps.sh(script: "az keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-USER' --query value -o tsv", returnStdout: true).trim()

    gradle("-Pdburl='${dbHost}:${dbPort}/${dbName}' -Pflyway.user='${dbUser}' -Pflyway.password='${dbPass}' migratePostgresDatabase")
  }

}
