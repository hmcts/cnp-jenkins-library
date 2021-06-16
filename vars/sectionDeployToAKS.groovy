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

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType

  def pactBrokerUrl = params.pactBrokerUrl
  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def aksUrl
  def environment = params.environment
  def acr
  def dockerImage
  def imageRegistry
  def projectBranch = new ProjectBranch(env.BRANCH_NAME)

  Builder builder = pipelineType.builder

  withAcrClient(subscription) {
    imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
    acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
    dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
    onPR {
      acr.retagForStage(DockerImage.DeploymentStage.PR, dockerImage)
    }
  }

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

  if (config.serviceApp) {
    withSubscriptionLogin(subscription) {
        withTeamSecrets(config, environment) {
          stageWithAgent("Smoke Test - AKS ${environment}", product) {
            testEnv(aksUrl) {
              pcr.callAround("smoketest:${environment}") {
                timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke Test - AKS') {
                  builder.smokeTest()
                }
              }
            }
          }

          onFunctionalTestEnvironment(environment) {
            stageWithAgent("Functional Test - AKS ${environment}", product) {
              testEnv(aksUrl) {
                pcr.callAround("functionalTest:${environment}") {
                  timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test - AKS') {
                    builder.functionalTest()
                  }
                }
              }
            }
          }
          if (config.performanceTest) {
            stageWithAgent("Performance Test - AKS ${environment}", product) {
              testEnv(aksUrl) {
                pcr.callAround("performanceTest:${environment}") {
                  timeoutWithMsg(time: 120, unit: 'MINUTES', action: "Performance Test - ${environment} (staging slot)") {
                    builder.performanceTest()
                    publishPerformanceReports(this, params)
                  }
                }
              }
            }
          }
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
              env.PACT_BROKER_URL = pactBrokerUrl
              pcr.callAround('pact-provider-verification') {
                  builder.runProviderVerification(pactBrokerUrl, version, isOnMaster)
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
                  pcr.callAround("crossBrowserTest:${environment}") {
                    builder.fullFunctionalTest()
                  }
                }
              }
            }
          }
          def nonProdEnv = new Environment(env).nonProdName
          def githubApi = new GithubAPI(this)
          if (environment == nonProdEnv || githubApi.checkForDependenciesLabel(env.BRANCH_NAME)) {
            helmUninstall(dockerImage, params, pcr)
          }
        }
      }
    }
}
