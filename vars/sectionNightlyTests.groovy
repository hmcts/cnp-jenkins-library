import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.Environment


def call(PipelineCallbacksRunner pcr, AppPipelineConfig config, PipelineType pipelineType, String product) {

  Environment environment = new Environment(env)

  withTeamSecrets(config, environment.nonProdName) {
    Builder builder = pipelineType.builder

    stageWithAgent('Checkout', product) {
      pcr.callAround('checkout') {
        checkoutScm()
      }
    }

    stageWithAgent("Build", product) {
      pcr.callAround('build') {
        timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
          builder.setupToolVersion()

          builder.build()
        }
      }
    }

    stageWithAgent('Dependency check', product) {
      warnError('Failure in DependencyCheckNightly') {
        pcr.callAround('DependencyCheckNightly') {
          timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Dependency check') {
            builder.securityCheck()
          }
        }
      }
    }

    if (config.crossBrowserTest) {
      stageWithAgent("Cross browser tests", product) {
        warnError('Failure in crossBrowserTest') {
          pcr.callAround('crossBrowserTest') {
            timeoutWithMsg(time: config.crossBrowserTestTimeout, unit: 'MINUTES', action: 'Cross browser test') {
              builder.crossBrowserTest()
            }
          }
        }
      }
    }

    if (config.performanceTest) {
      stageWithAgent("Performance test", product) {
        warnError('Failure in performanceTest') {
          pcr.callAround('PerformanceTest') {
            timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
              builder.performanceTest()
            }
          }
        }
      }
    }

    if (config.securityScan) {
      stageWithAgent('Security scan', product) {
        warnError('Failure in securityScan') {
          pcr.callAround('securityScan') {
            timeout(time: config.securityScanTimeout, unit: 'MINUTES') {
              builder.securityScan()
            }
          }
        }
      }
    }

    if (config.mutationTest) {
      stageWithAgent('Mutation tests', product) {
        warnError('Failure in mutationTest') {
          pcr.callAround('mutationTest') {
            timeoutWithMsg(time: config.mutationTestTimeout, unit: 'MINUTES', action: 'Mutation test') {
              builder.mutationTest()
            }
          }
        }
      }
    }

    if (config.fullFunctionalTest) {
      stageWithAgent('Full functional tests', product) {
        warnError('Failure in fullFunctionalTest') {
          pcr.callAround('fullFunctionalTest') {
            timeoutWithMsg(time: config.fullFunctionalTestTimeout, unit: 'MINUTES', action: 'Functional tests') {
              builder.fullFunctionalTest()
            }
          }
        }
      }
    }

    if (currentBuild.result == "UNSTABLE" || currentBuild.result == "FAILURE") {
      error "At least one stage failed, check the logs to see why"
    }
  }
}
