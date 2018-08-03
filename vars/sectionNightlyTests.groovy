import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType


def call(PipelineCallbacks pl, PipelineType pipelineType) {

  Builder builder = pipelineType.builder

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

  try {
    stage('DependencyCheckNightly') {
      pl.callAround('DependencyCheckNightly') {
        timeout(time: 15, unit: 'MINUTES') {
          builder.securityCheck()
        }
      }
    }
  } catch(err) {
    err.printStackTrace()
    currentBuild.result = "UNSTABLE"
  }

  if (pl.crossBrowserTest) {
    try {
      stage("crossBrowserTest") {
        pl.callAround('crossBrowserTest') {
          timeout(time: pl.crossBrowserTestTimeout, unit: 'MINUTES') {
            builder.crossBrowserTest()
          }
        }
      }
    }
     catch (err) {
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

  if (pl.mutationTest) {
    try {
      stage('mutationTest') {
        pl.callAround('mutationTest') {
          timeout(time: pl.mutationTestTimeout, unit: 'MINUTES') {
            builder.mutationTest()
          }
        }
      }
    }
    catch(err) {
      err.printStackTrace()
      currentBuild.result = "UNSTABLE"
    }
  }

}
