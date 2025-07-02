import uk.gov.hmcts.contino.Environment


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
      //The rerun stage will only become active if config.reRunOnFail is true
      def stages = ['Performance test', 'Test Rerun']
      def amountOfReruns = (reRunOnFail== true) ? 2 : 1
      for (int i = 0; i < amountOfReruns; i++) {
        stageWithAgent(stages[i], product) {
          warnError('Failure in performanceTest') {
            pcr.callAround('PerformanceTest') {
              timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
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
      //Gatling alerts will only become active if config.gatlingAlerts is set to true
      if (config.gatlingAlerts == true) and (checkIfGatlingTestFailedThenReport("${config.slackUserID}") == false)
        checkIfGatlingTestFailedIntermitentlyThenReport("${config.slackUserID}", 10)
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
