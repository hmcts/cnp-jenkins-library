import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.Environment


def call(PipelineCallbacks pl, PipelineType pipelineType) {

  Environment environment = new Environment(env)

  withTeamSecrets(pl, environment.nonProdName) {
    Builder builder = pipelineType.builder

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

    try {
      stage('DependencyCheckNightly') {
        pl.callAround('DependencyCheckNightly') {
          timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Dependency check') {
            builder.securityCheck()
          }
        }
      }
    } catch (err) {
      err.printStackTrace()
      currentBuild.result = "UNSTABLE"
    }

    if (pl.crossBrowserTest) {
      try {
        stage("crossBrowserTest") {
          pl.callAround('crossBrowserTest') {
            timeoutWithMsg(time: pl.crossBrowserTestTimeout, unit: 'MINUTES', action: 'Cross browser test') {
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
            timeoutWithMsg(time: pl.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
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
            timeoutWithMsg(time: pl.mutationTestTimeout, unit: 'MINUTES', action: 'Mutation test') {
              builder.mutationTest()
            }
          }
        }
      }
      catch (err) {
        err.printStackTrace()
        currentBuild.result = "UNSTABLE"
      }
    }

    if (pl.fullFunctionalTest) {
      try {
        stage('fullFunctionalTest') {
          pl.callAround('fullFunctionalTest') {
            timeoutWithMsg(time: pl.fullFunctionalTestTimeout, unit: 'MINUTES', action: 'Functional tests') {
              builder.fullFunctionalTest()
            }
          }
        }
      }
      catch (err) {
        err.printStackTrace()
        currentBuild.result = "UNSTABLE"
      }
    }

  }
}
