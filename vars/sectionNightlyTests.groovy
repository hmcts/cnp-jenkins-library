import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.Environment


def call(PipelineCallbacksRunner pcr, AppPipelineConfig config, PipelineType pipelineType) {

  Environment environment = new Environment(env)

  withTeamSecrets(config, environment.nonProdName) {
    Builder builder = pipelineType.builder

    stage('Checkout') {
      pcr.callAround('checkout') {
        deleteDir()
        checkout scm
      }
    }

    stage("Build") {
      pcr.callAround('build') {
        timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
          builder.setupToolVersion()

          builder.build()
        }
      }
    }

    try {
      stage('DependencyCheckNightly') {
        pcr.callAround('DependencyCheckNightly') {
          timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Dependency check') {
            builder.securityCheck()
          }
        }
      }
    } catch (err) {
      echo "Failure in DependencyCheckNightly, continuing build will fail at the end"
    }

    if (config.crossBrowserTest) {
      try {
        stage("crossBrowserTest") {
          pcr.callAround('crossBrowserTest') {
            timeoutWithMsg(time: config.crossBrowserTestTimeout, unit: 'MINUTES', action: 'Cross browser test') {
              builder.crossBrowserTest()
            }
          }
        }
      }
      catch (err) {
        echo "Failure in crossBrowserTest, continuing build will fail at the end"
      }
    }

    if (config.performanceTest) {
      try {
        stage("performanceTest") {
          pcr.callAround('PerformanceTest') {
            timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
              builder.performanceTest()
            }
          }
        }
      } catch (err) {
        echo "Failure in performanceTest, continuing build will fail at the end"
      }
    }

    if (config.securityScan) {
      try {
        stage('securityScan') {
          pcr.callAround('securityScan') {
            timeout(time: config.securityScanTimeout, unit: 'MINUTES') {
              builder.securityScan()
            }
          }
        }
      }
      catch (err) {
        echo "Failure in securityScan, continuing build will fail at the end"
      }
    }

    if (config.mutationTest) {
      try {
        stage('mutationTest') {
          pcr.callAround('mutationTest') {
            timeoutWithMsg(time: config.mutationTestTimeout, unit: 'MINUTES', action: 'Mutation test') {
              builder.mutationTest()
            }
          }
        }
      }
      catch (err) {
        echo "Failure in mutationTest, continuing build will fail at the end"
      }
    }

    if (config.fullFunctionalTest) {
      try {
        stage('fullFunctionalTest') {
          pcr.callAround('fullFunctionalTest') {
            timeoutWithMsg(time: config.fullFunctionalTestTimeout, unit: 'MINUTES', action: 'Functional tests') {
              builder.fullFunctionalTest()
            }
          }
        }
      }
      catch (err) {
        echo "Failure in fullFunctionalTest, continuing build will fail at the end"
      }
    }

    if (currentBuild.result == "FAILURE") {
      error "At least one stage failed, check the logs to see why"
    }
  }
}
