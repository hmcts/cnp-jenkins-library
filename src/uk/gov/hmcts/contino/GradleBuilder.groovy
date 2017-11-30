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
    steps.stash(name: product, includes: "build/libs/*.jar")
  }

  def test() {
    try {
      gradle("check")
    } finally {
      junit 'build/test-results/**/*.xml'
    }
  }

  def sonarScan() {
      gradle("--info sonarqube")
  }

  def smokeTest() {

  }

  def securityCheck() {

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
}
