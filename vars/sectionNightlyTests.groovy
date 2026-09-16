import com.cloudbees.groovy.cps.NonCPS
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor
import uk.gov.hmcts.contino.Environment

@NonCPS
boolean isTriggeredByTimer() {
  def causes = currentBuild.rawBuild?.getCauses() ?: []
  return causes.any {
    it.getClass().getName().contains('TimerTriggerCause')
  }
}

def call(pcr, config, pipelineType, String product, String component, String subscription) {

  Environment environment = new Environment(env)
  String testEnvironment = environment.nonProdName
  String testSubscription = subscription
  def nightlyDeployment

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

    String originalTestUrl = env.TEST_URL
    String originalAksTestUrl = env.AKS_TEST_URL
    String originalEnvironmentName = env.ENVIRONMENT_NAME

    try {
      if (config.nightlyDeployment) {
        nightlyDeployment = sectionDeployNightlyInstance(
          pipelineCallbacksRunner: pcr,
          appPipelineConfig: config,
          pipelineType: pipelineType,
          product: product,
          component: component
        )
        testEnvironment = nightlyDeployment.environment
        testSubscription = nightlyDeployment.subscription
        env.TEST_URL = nightlyDeployment.url
        env.AKS_TEST_URL = nightlyDeployment.url
        env.ENVIRONMENT_NAME = nightlyDeployment.environment
      }

      withTeamSecrets(config, testEnvironment) {
        runNightlyTestStages(pcr, config, builder, product, component, testEnvironment, testSubscription)
      }
    } finally {
      if (nightlyDeployment && !config.keepNightlyDeployment) {
        helmUninstall(nightlyDeployment.dockerImage, nightlyDeployment.deployParams, pcr)
      }
      env.TEST_URL = originalTestUrl ?: ''
      env.AKS_TEST_URL = originalAksTestUrl ?: ''
      env.ENVIRONMENT_NAME = originalEnvironmentName ?: ''
    }
  }
}

def runNightlyTestStages(pcr, config, builder, String product, String component, String testEnvironment, String testSubscription) {
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

  if (config.e2eTest) {
    stageWithAgent("End to End test", product) {
      pcr.callAround('E2eTest') {
        builder.e2eTest()
      }
    }
  }

  if (config.performanceTest) {
    boolean triggeredByTimer = isTriggeredByTimer()
    boolean doSecondRun = false
    def stages = ['Performance test', 'Failed Test Rerun']

    for (int i = 0; i < stages.size(); i++) {
      stageWithAgent(stages[i], product) {
        warnError('Failure in performanceTest') {
          pcr.callAround('PerformanceTest') {
            timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: stages[i]) {
              if ((i == 0) && triggeredByTimer && config.perfRerunOnFail) {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                  try {
                    builder.performanceTest()
                  } catch (e) {
                    doSecondRun = true
                    throw e
                  }
                }
              } else {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                  builder.performanceTest()
                }
              }

              publishPerformanceReports(
                product: product,
                component: component,
                environment: testEnvironment,
                subscription: testSubscription,
                folder: "nightly"
              )
            }
          }
        }
      }

      if (!(triggeredByTimer && config.perfRerunOnFail && doSecondRun)) {
        break
      }
    }

    if (config.perfGatlingAlerts == true) {
      performanceCheckIfTestFailed("${config.perfSlackChannel}")
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
    environment: testEnvironment,
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
