import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, Builder builder) {

  stage('Checkout') {
    pl.callAround('checkout') {
      deleteDir()
      checkout scm
    }
  }

  if (pl.crossBrowserTest) {
    try {
      stage("Build") {
        pl.callAround('build') {
          timeout(time: 15, unit: 'MINUTES') {
            builder.build()
          }
        }
      }
      stage("crossBrowserTest") {
        pl.callAround('crossBrowserTest') {
          timeout(time: pl.crossBrowserTestTimeout, unit: 'MINUTES') {
            builder.crossBrowserTest()
          }
        }
      }
    } catch (err) {
      err.printStackTrace()
      currentBuild.result = "UNSTABLE"
    }
  }

  if (pl.performanceTest) {
    try {
      stage("performanceTest") {
        pl.callAround('PerformanceTest') {
          timeout(time: pl.perfTestTimeout, unit: 'MINUTES') {
            builder.performanceTest()
          }
        }
      }
    } catch (err) {
      err.printStackTrace()
      currentBuild.result = "UNSTABLE"
    }
  }
}




