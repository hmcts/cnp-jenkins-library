import uk.gov.hmcts.contino.NightlyBuilder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, NightlyBuilder builder) {

  stage('Checkout') {
    pl.callAround('checkout') {
      deleteDir()
      checkout scm
    }
  }

  stage("performanceTest") {
    pl.callAround('PerformanceTest') {
      timeout(time: 15, unit: 'MINUTES') {
        builder.performanceTest()
      }
    }
  }

}

