import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.SlackAlerts


def call(pcr, config, pipelineType, String product, String component, String subscription) {

  Environment environment = new Environment(env)

  withTeamSecrets(config, environment.nonProdName) {
    def builder = pipelineType.builder

    stageWithAgent('Checkout', product) {
      checkoutScm(pipelineCallbacksRunner: pcr)
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

    if (config.fortifyScan) {
      fortifyScan(
        pipelineCallbacksRunner: pcr,
        fortifyVaultName: config.fortifyVaultName ?: "${product}-${environment.nonProdName}",
        builder: builder,
        product: product,
      )
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

    if (config.parallelCrossBrowsers) {
      Set<String> browsers = config.parallelCrossBrowsers.toSet()
      Map crossBrowserStages = [:]
      browsers.each { browser ->
        crossBrowserStages.put(browser.capitalize(), {
          warnError('Failure in crossBrowserTest') {
            pcr.callAround('crossBrowserTest') {
              timeoutWithMsg(time: config.crossBrowserTestTimeout, unit: 'MINUTES', action: 'Cross browser test') {
                builder.crossBrowserTest(browser)
              }
            }
          }
        })
      }
      stageWithAgent('Cross browser tests', product) {
        parallel(crossBrowserStages)
      }
    }

    if (config.performanceTest) {
      def stages = ['Performance test', 'Test Rerun']
      for (int i = 0; i < 2; i++) {
        stageWithAgent(stages[i], product) {
          warnError('Failure in performanceTest') {
            pcr.callAround('PerformanceTest') {
              timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
                echo "YR: Starting test"
                builder.performanceTest()
                publishPerformanceReports(
                  product: product,
                  component: component,
                  environment: environment.nonProdName,
                  subscription: subscription
                )
              }
            }
          }
        }
      }


      /*stageWithAgent("Performance test", product) {
        warnError('Failure in performanceTest') {
          pcr.callAround('PerformanceTest') {
            timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
              echo "YR: Starting test"
              builder.performanceTest()
              publishPerformanceReports(
                product: product,
                component: component,
                environment: environment.nonProdName,
                subscription: subscription
              )
            }
          }
        }

        echo "YR: Ending test 1"

        if (config.reRunOnFail == true) {
          echo "YR: Inside reRunOnFail"
          stageWithAgent("Test Rerun", product) {
            warnError('Failure in performanceTest') {
              pcr.callAround('PerformanceTest') {
                timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
                  echo "YR: Starting test 2"
                  builder.performanceTest()
                  publishPerformanceReports(
                    product: product,
                    component: component,
                    environment: environment.nonProdName,
                    subscription: subscription
                  )
                }
              }
            }
          }
        }
        echo "YR: Ending test 2"*/
      if (config.gatlingAlerts == true) {
        def testFailed = checkIfGatlingTestFailedThenReport("${config.slackUserID}")
        echo "YR: After function 1"
        if (testFailed == false) {
          checkIfGatlingTestFailedIntermitentlyThenReport("${config.slackUserID}", 10)
          echo "YR: After function 2"
        }
      }
    }

    if (config.securityScan) {
      stageWithAgent('Security scan', product) {
        warnError('Failure in securityScan') {
          env.ZAP_URL_EXCLUSIONS = config.securityScanUrlExclusions
          env.ALERT_FILTERS = config.securityScanAlertFilters
          env.SCAN_TYPE = config.securityScanType
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

    highLevelDataSetup(
      appPipelineConfig: config,
      pipelineCallbacksRunner: pcr,
      builder: builder,
      environment: environment.nonProdName,
      product: product,
    )

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
