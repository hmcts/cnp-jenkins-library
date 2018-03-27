#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, Builder builder, Subscription subscription) {
  stage('Checkout') {
    pl.callAround('checkout') {
      deleteDir()
      checkout scm
    }
  }

  stage("Build") {
    pl.callAround('build') {
      timeout(time: 15, unit: 'MINUTES') {
        builder.build()
      }
    }
  }

  stage("Test") {
    pl.callAround('test') {
      timeout(time: 15, unit: 'MINUTES') {
        builder.test()
      }
    }
  }

  stage("Security Checks") {
    pl.callAround('securitychecks') {
      builder.securityCheck(subscription)
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
