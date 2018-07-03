import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, Builder builder) {

  def caughtException

  stage('Checkout') {
    pl.callAround('checkout') {

      sh "ls -la ${WORKSPACE}/src/gatling"
      sh "ls -la ${WORKSPACE}/src/gatling/conf"

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
      caughtException = err
      currentBuild.result = "UNSTABLE"
    }
  }

  if (pl.peformanceTest) {
    stage("performanceTest") {
      pl.callAround('PerformanceTest') {
        timeout(time: pl.perfTestTimeout, unit: 'MINUTES') {
          builder.performanceTest()
        }
      }
    }
  }

  if (caughtException) {
    throw caughtException
  }
}




