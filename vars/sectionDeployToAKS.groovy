import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.securityRules

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
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType

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

  SecurityRules securityRules = new SecurityRules(this)
  def rules = securityRules.getSecurityRules
  
  Builder builder = pipelineType.builder

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
              warnAboutDeprecatedChartConfig product: product, component: component
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
          env.PACT_BROKER_URL = env.PACT_BROKER_URL ?: 'pact-broker.platform.hmcts.net'
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
              pcr.callAround("smoketest:${environment}") {
                timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke Test - AKS') {
                  def success = true
                  try {
                    builder.smokeTest()
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
            }
          }

          onFunctionalTestEnvironment(environment) {
            if (testLabels.contains('enable_full_functional_tests')) {
              stageWithAgent('Functional test (Full)', product) {
                testEnv(aksUrl) {
                  warnError('Failure in fullFunctionalTest') {
                    pcr.callAround("fullFunctionalTest:${environment}") {
                      timeoutWithMsg(time: config.fullFunctionalTestTimeout, unit: 'MINUTES', action: 'Functional tests') {
                        def success = true
                        try {
                          builder.fullFunctionalTest()
                        } catch (err) {
                          success = false
                          throw err
                        } finally {
                          savePodsLogs(dockerImage, params, "full-functional")
                          if (!success) {
                            clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                          }
                        }
                      }
                    }
                  }
                }
              }
            } else {
              stageWithAgent("Functional Test - ${environment}", product) {
                testEnv(aksUrl) {
                  pcr.callAround("functionalTest:${environment}") {
                    timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test - AKS') {
                      def success = true
                      try {
                        builder.functionalTest()
                      } catch (err) {
                        success = false
                        throw err
                      } finally {
                        savePodsLogs(dockerImage, params, "functional")
                        if (!success) {
                          clearHelmReleaseForFailure(enableHelmLabel, config, dockerImage, params, pcr)
                        }
                      }
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
                    pcr.callAround('securityScan') {
                      timeout(time: config.securityScanTimeout, unit: 'MINUTES') {
                        builder.securityScan()
                      }
                      securityRules: rules
                    }
                  }
                }
              }
            }
          }
        }
      }
      def triggerUninstall = environment == nonProdEnv
      if (triggerUninstall || !enableHelmLabel) {
        helmUninstall(dockerImage, params, pcr)
      }
    }
  }
}
