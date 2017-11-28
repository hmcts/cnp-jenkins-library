package uk.gov.hmcts.contino

class Versioner implements Serializable {
  def steps

  Versioner(steps) {
    this.steps = steps
  }

  def addNodeVersionInfo() {
    steps.sh '''tee version <<EOF
version: $(node -pe 'require("./package.json").version')
build: ${BUILD_NUMBER}
commit: $(git rev-parse HEAD)
date: $(date)
EOF
    '''
  }

  def addJavaVersionInfo() {
    steps.sh '''
mkdir -p src/main/resources/META-INF
echo "allprojects { task printVersionInit { doLast { println project.version } } }" > init.gradle

tee src/main/resources/META-INF/build-info.properties <<EOF
build.version=$(./gradlew --init-script init.gradle -q :printVersionInit)
build.number=${BUILD_NUMBER}
build.commit=$(git rev-parse HEAD)
build.date=$(date)
EOF

'''
  }

}

