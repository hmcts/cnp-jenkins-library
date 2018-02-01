#!groovy
import uk.gov.hmcts.contino.Builder

def call(Builder builder) {
  stage('Checkout') {
    pl.callAround('checkout') {
      deleteDir()
      checkout scm
    }
  }

  stage("Build") {
    pl.callAround('build') {
      builder.build()
    }
  }

  stage("Test") {
    pl.callAround('test') {
      builder.test()
    }
  }

  stage("Security Checks") {
    pl.callAround('securitychecks') {
      builder.securityCheck()
    }
  }

  stage("Sonar Scan") {
    pl.callAround('sonarscan') {
      pluginActive('sonar') {
        withSonarQubeEnv("SonarQube") {
          builder.sonarScan()
        }

        timeout(time: 5, unit: 'MINUTES') {
          def qg = steps.waitForQualityGate()
          if (qg.status != 'OK') {
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
          }
        }
      }
    }
  }
}
