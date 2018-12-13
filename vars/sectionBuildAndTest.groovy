#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, Builder builder) {
  stage('Checkout') {
    pl.callAround('checkout') {
      deleteDir()
      checkout scm
    }
  }

  stage("Build") {
    pl.callAround('build') {
      timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
        builder.build()
      }
    }
  }

  stage("Tests and checks") {

    timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'Tests and checks') {

      parallel(
        "Unit tests and Sonar scan": {

          pl.callAround('test') {
            builder.test()
          }

          pl.callAround('sonarscan') {
            pluginActive('sonar') {
              withSonarQubeEnv("SonarQube") {
                builder.sonarScan()
              }
            }
          }

        },

        "Security Checks": {
          pl.callAround('securitychecks') {
            builder.securityCheck()
          }
        }
      )

      // Quality Gate check
      def qg = waitForQualityGate()
      if (qg.status != 'OK') {
        error "Pipeline aborted due to quality gate failure: ${qg.status}"
      }
    }



  }

}
