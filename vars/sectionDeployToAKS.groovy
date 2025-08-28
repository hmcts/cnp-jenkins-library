import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.GithubAPI

def testEnv(String testUrl, block) {
  def testEnv = new Environment(env).nonProdName
  def testEnvVariables = ["TEST_URL=${testUrl}","ENVIRONMENT_NAME=${testEnv}"]

  withEnv(testEnvVariables) {
    echo "Using TEST_URL: ${env.TEST_URL}"
    echo "Using ENVIRONMENT_NAME: ${env.ENVIRONMENT_NAME}"
    block.call()
  }
}

def clearHelmReleaseForFailure(boolean enableHelmLabel, AppPipelineConfig config, DockerImage dockerImage, Map params, PipelineCallbacksRunner pcr) {
    def projectBranch = new ProjectBranch(env.BRANCH_NAME)
    if ((projectBranch.isMaster() && config.clearHelmReleaseOnFailure) || (projectBranch.isPR() && !enableHelmLabel)) {
        helmUninstall(dockerImage, params, pcr)
  }
}

def call(params) {
  def pcr = params.pipelineCallbacksRunner
  def config = params.appPipelineConfig
  def pipelineType = params.pipelineType

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def aksUrl
  def environment = params.environment
  def acr
  def dockerImage
  def imageRegistry
  def projectBranch = new ProjectBranch(env.BRANCH_NAME)
  def nonProdEnv = new Environment(env).nonProdName

  def builder = pipelineType.builder

  withAcrClient(subscription) {
    imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
    acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
    dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
  }

  def deploymentNamespace = projectBranch.deploymentNamespace()
  def deploymentProduct = deploymentNamespace ? "$deploymentNamespace-$product" : product

  GithubAPI gitHubAPI = new GithubAPI(this)
  def testLabels = gitHubAPI.getLabelsbyPattern(env.BRANCH_NAME, 'enable_')
  boolean enableHelmLabel = testLabels.contains('enable_keep_helm')

  lock("${deploymentProduct}-${component}-${environment}-deploy") {
    stageWithAgent("AKS deploy - ${environment}", product) {
      withTeamSecrets(config, environment) {
        pcr.callAround('akschartsinstall') {
          withAksClient(subscription, environment, product) {
            timeoutWithMsg(time: 25, unit: 'MINUTES', action: 'Install Charts to AKS') {
              onPR {
                deploymentNumber = githubCreateDeployment()
              }
              params.environment = params.environment.replace('idam-', '') // hack to workaround incorrect idam environment value
              log.info("Using AKS environment: ${params.environment}")
              warnAboutDeprecatedChartConfig(product: product, component: component, repoUrl: (env.GIT_URL ?: 'unknown'))
              aksUrl = helmInstall(dockerImage, params)
              log.info("deployed component URL: ${aksUrl}")
              onPR {
                githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
              }
            }
          }
        }
      }
    }
    onPR {
      highLevelDataSetup(
        appPipelineConfig: config,
        pipelineCallbacksRunner: pcr,
        builder: builder,
        environment: environment,
        product: product,
      )
    }
    withSubscriptionLogin(subscription) {
      if (config.pactBrokerEnabled && config.pactConsumerCanIDeployEnabled) {
        stageWithAgent("Pact Consumer Can I Deploy", product) {
          builder.runConsumerCanIDeploy()
        }
      }
      if (config.pactBrokerEnabled && config.pactProviderVerificationsEnabled) {
        stageWithAgent("Pact Provider Verification", product) {
          def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
          def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

          env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
          env.PACT_BROKER_URL = env.PACT_BROKER_URL ?: 'https://pact-broker.platform.hmcts.net'
          env.PACT_BROKER_SCHEME = env.PACT_BROKER_SCHEME ?: 'https'
          env.PACT_BROKER_PORT = env.PACT_BROKER_PORT ?: '443'
          pcr.callAround('pact-provider-verification') {
            builder.runProviderVerification(env.PACT_BROKER_URL, version, isOnMaster)
          }
        }
      }
      if (config.serviceApp) {
        withTeamSecrets(config, environment) {
          stageWithAgent("Smoke Test - AKS ${environment}", product) {
            testEnv(aksUrl) {
              def success = true
              try {
                pcr.callAround("smoketest:${environment}") {
                  timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke Test - AKS') {
                    builder.smokeTest()
                  }
                }
              } catch (err) {
                success = false
                throw err
              } finally {
                savePodsLogs(dockerImage, params, "smoke")
                if (!success) {
                  clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                }
              }
            }
          }

          onFunctionalTestEnvironment(environment) {
            if (testLabels.contains('enable_full_functional_tests')) {
              stageWithAgent('Functional test (Full)', product) {
                testEnv(aksUrl) {
                  warnError('Failure in fullFunctionalTest') {
                    def success = true
                    try {
                      pcr.callAround("fullFunctionalTest:${environment}") {
                        timeoutWithMsg(time: config.fullFunctionalTestTimeout, unit: 'MINUTES', action: 'Functional tests') {
                          builder.fullFunctionalTest()
                        }
                      }
                    } catch (err) {
                      success = false
                      throw err
                    } finally {
                      savePodsLogs(dockerImage, params, "full-functional")
                      if (!success) {
                        clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                        error('Functional test failed')
                      }
                    }
                  }
                }
              }
            } else {
              stageWithAgent("Functional Test - ${environment}", product) {
                testEnv(aksUrl) {
                  def success = true
                  try {
                    pcr.callAround("functionalTest:${environment}") {
                      timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test - AKS') {
                        builder.functionalTest()
                      }
                    }
                  } catch (err) {
                    success = false
                    throw err
                  } finally {
                    savePodsLogs(dockerImage, params, "functional")
                    if (!success) {
                      clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                      error('Functional test failed')
                    }
                  }
                }
              }
            }
          }
          if (config.performanceTest) {
            stageWithAgent("Performance Test - ${environment}", product) {
              testEnv(aksUrl) {
                pcr.callAround("performanceTest:${environment}") {
                  timeoutWithMsg(time: 120, unit: 'MINUTES', action: "Performance Test - ${environment} (staging slot)") {
                    builder.performanceTest()
                    publishPerformanceReports(params)
                  }
                }
              }
            }
          }
          // Performance Test Pipeline: Setup -> Parallel Testing
          if (config.performanceTestStages || config.gatlingLoadTests) {
            
            // Load performance test secrets once for all stages
            def perfKeyVaultUrl = "https://et-perftest.vault.azure.net/"
            def perfSecrets = [
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-synthetic-monitor-token', version: '', envVariable: 'PERF_SYNTHETIC_MONITOR_TOKEN'],
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-metrics-token', version: '', envVariable: 'PERF_METRICS_TOKEN'],
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-event-token', version: '', envVariable: 'PERF_EVENT_TOKEN'],
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-synthetic-update-token', version: '', envVariable: 'PERF_SYNTHETIC_UPDATE_TOKEN']
            ]
            
            withAzureKeyvault(
              azureKeyVaultSecrets: perfSecrets,
              keyVaultURLOverride: perfKeyVaultUrl
            ) {
            
              // Stage 1: Dynatrace Setup - Post build info, events, and metrics first
              if (config.performanceTestStages) {
                stageWithAgent("Dynatrace Performance Setup - ${environment}", product) {
                  testEnv(aksUrl) {
                    def success = true
                    try {
                      pcr.callAround("dynatracePerformanceSetup:${environment}") {
                        timeoutWithMsg(time: 5, unit: 'MINUTES', action: "Dynatrace Performance Setup - ${environment}") {
                          dynatracePerformanceSetup([
                            product: product,
                            component: component,
                            environment: environment,
                            testUrl: env.TEST_URL,
                            secrets: config.vaultSecrets,
                            configPath: config.performanceTestConfigPath
                          ])
                        }
                      }
                    } catch (err) {
                      success = false
                      echo "Dynatrace setup failed: ${err.message}"
                      // Don't fail the build for setup issues, continue with tests
                    } finally {
                      savePodsLogs(dockerImage, params, "dynatrace-setup")
                    }
                  }
                }
              }
            
            // Stage 2: Run performance tests in parallel (if both enabled) or sequential (if only one enabled)
            def testStages = [:]
            
            if (config.performanceTestStages) {
              testStages['Dynatrace Synthetic Tests'] = {
                stageWithAgent("Dynatrace Synthetic Tests - ${environment}", product) {
                  testEnv(aksUrl) {
                    def success = true
                    try {
                      pcr.callAround("dynatraceSyntheticTest:${environment}") {
                        timeoutWithMsg(time: config.performanceTestStagesTimeout, unit: 'MINUTES', action: "Dynatrace Synthetic Tests - ${environment}") {
                          dynatraceSyntheticTest([
                            product: product,
                            component: component,
                            environment: environment,
                            testUrl: env.TEST_URL,
                            secrets: config.vaultSecrets,
                            configPath: config.performanceTestConfigPath,
                            performanceTestStagesEnabled: config.performanceTestStages,
                            gatlingLoadTestsEnabled: config.gatlingLoadTests
                          ])
                        }
                      }
                    } catch (err) {
                      success = false
                      throw err
                    } finally {
                      savePodsLogs(dockerImage, params, "dynatrace-synthetic")
                      if (!success) {
                        clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                      }
                    }
                  }
                }
              }
            }
            
            if (config.gatlingLoadTests) {
              testStages['Gatling Load Tests'] = {
                stageWithAgent("Gatling Load Tests - ${environment}", product) {
                  testEnv(aksUrl) {
                    def success = true
                    try {
                      pcr.callAround("gatlingLoadTests:${environment}") {
                        timeoutWithMsg(time: config.gatlingLoadTestTimeout, unit: 'MINUTES', action: "Gatling Load Tests - ${environment}") {
                          gatlingExternalLoadTest([
                            product: product,
                            component: component,
                            environment: environment,
                            subscription: subscription,
                            gatlingRepo: config.gatlingRepo,
                            gatlingBranch: config.gatlingBranch,
                            gatlingTestPath: config.gatlingTestPath,
                            gatlingSimulation: config.gatlingSimulation,
                            gatlingUsers: config.gatlingUsers,
                            gatlingRampDuration: config.gatlingRampDuration,
                            gatlingTestDuration: config.gatlingTestDuration,
                            testUrl: env.TEST_URL,
                            performanceTestStagesEnabled: config.performanceTestStages,
                            gatlingLoadTestsEnabled: config.gatlingLoadTests
                          ])
                        }
                      }

                    } catch (err) {
                      success = false
                      throw err
                    } finally {
                      savePodsLogs(dockerImage, params, "gatling-load-tests")
                      if (!success) {
                        clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                      }
                    }
                  }
                }
              }
            }
            
              // Execute test stages
              if (testStages.size() > 1) {
                echo "Running Dynatrace Synthetic Tests and Gatling Load Tests in parallel..."
                parallel(testStages)
              } else {
                echo "Running single performance test stage..."
                testStages.values().first().call()
              }
              
            } // End withAzureKeyvault block
          }

          onMaster {
            if (config.crossBrowserTest) {
              stageWithAgent("CrossBrowser Test - AKS ${environment}", product) {
                testEnv(aksUrl) {
                  pcr.callAround("crossBrowserTest:${environment}") {
                    builder.crossBrowserTest()
                  }
                }
              }
            }
            if (config.mutationTest) {
              stageWithAgent("Mutation Test - AKS ${environment}", product) {
                testEnv(aksUrl) {
                  pcr.callAround("mutationTest:${environment}") {
                    builder.mutationTest()
                  }
                }
              }
            }
            if (config.fullFunctionalTest) {
              stageWithAgent("FullFunctional Test - AKS ${environment}", product) {
                testEnv(aksUrl) {
                  pcr.callAround("fullFunctionalTest:${environment}") {
                    builder.fullFunctionalTest()
                  }
                }
              }
            }
          }


          onPR {
            if (testLabels.contains('enable_performance_test')) {
              stageWithAgent("Performance test", product) {
                warnError('Failure in performanceTest') {
                  pcr.callAround('PerformanceTest') {
                    timeoutWithMsg(time: config.perfTestTimeout, unit: 'MINUTES', action: 'Performance test') {
                      try {
                        builder.performanceTest()
                      } finally {
                        savePodsLogs(dockerImage, params, "performance")
                      }
                    }
                  }
                }
              }
            }
            if (testLabels.contains('enable_security_scan')) {
              testEnv(aksUrl) {
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
            }
          }
        }
      }
      def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()
      if (isOnMaster || !enableHelmLabel) {
        helmUninstall(dockerImage, params, pcr)
      }
    }
  }
}
