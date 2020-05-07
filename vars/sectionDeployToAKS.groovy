import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.Environment

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

  Builder builder = pipelineType.builder

  withAcrClient(subscription) {
    acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
    dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag(), env.GIT_COMMIT)
    onPR {
      acr.retagForStage(DockerImage.DeploymentStage.PR, dockerImage)
    }
  }

  withSubscription(subscription) {
    withTeamSecrets(config, environment) {
      stage("AKS deploy - ${environment}") {
        pcr.callAround('akschartsinstall') {
          timeoutWithMsg(time: 25, unit: 'MINUTES', action: 'Install Charts to AKS') {
            onPR {
              deploymentNumber = githubCreateDeployment()
            }
            withAksClient(subscription, environment) {
              aksUrl = helmInstall(dockerImage, params)
              log.info("deployed component URL: ${aksUrl}")
            }
            onPR {
              githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
            }
          }
        }
      }
    }
  }

  if (config.serviceApp) {
      withSubscription(subscription) {
        withTeamSecrets(config, environment) {
          stage("Smoke Test - AKS ${environment}") {
            testEnv(aksUrl) {
              pcr.callAround("smoketest:${environment}") {
                timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke Test - AKS') {
                  builder.smokeTest()
                }
              }
            }
          }

          onFunctionalTestEnvironment(environment) {
            stage("Functional Test - AKS ${environment}") {
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
            stage("Performance Test - AKS ${environment}") {
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

          if (config.pactBrokerEnabled) {
            stage("Pact Provider Verification") {
              def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
              def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

              env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
              env.PACT_BROKER_URL = pactBrokerUrl

              if (config.pactProviderVerificationsEnabled) {
                pcr.callAround('pact-provider-verification') {
                  builder.runProviderVerification(pactBrokerUrl, version, isOnMaster)
                }
              }
            }
          }
          onMaster {
            if (config.crossBrowserTest) {
              stage("CrossBrowser Test - AKS ${environment}") {
                testEnv(aksUrl) {
                  pcr.callAround("crossBrowserTest:${environment}") {
                    builder.crossBrowserTest()
                  }
                }
              }
            }
            if (config.mutationTest) {
              stage("Mutation Test - AKS ${environment}") {
                testEnv(aksUrl) {
                  pcr.callAround("mutationTest:${environment}") {
                    builder.mutationTest()
                  }
                }
              }
            }
            if (config.fullFunctionalTest) {
              stage("FullFunctional Test - AKS ${environment}") {
                testEnv(aksUrl) {
                  pcr.callAround("crossBrowserTest:${environment}") {
                    builder.fullFunctionalTest()
                  }
                }
              }
            }
          }
        }
      }
    }
}
