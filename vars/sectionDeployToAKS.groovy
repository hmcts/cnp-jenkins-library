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

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def aksUrl
  def environment = params.environment

  Builder builder = pipelineType.builder

  if (config.dockerBuild) {
    def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
    def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag(), env.GIT_COMMIT)

    withAcrClient(subscription) {
      onPR {
        acr.retagForStage(DockerImage.DeploymentStage.PR, dockerImage)
      }
    }
    withAksClient(subscription, environment) {

      if (config.deployToAKS) {

        withTeamSecrets(config, environment) {
          stage('Deploy to AKS') {
            pcr.callAround('aksdeploy') {
              timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Deploy to AKS') {
                onPR {
                  deploymentNumber = githubCreateDeployment()
                }

                aksUrl = aksDeploy(dockerImage, params)
                log.info("deployed component URL: ${aksUrl}")

                onPR {
                  githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
                }
              }
            }
          }
        }
      } else if (config.installCharts) {
        withTeamSecrets(config, environment) {
          stage("AKS deploy - ${environment}") {
            pcr.callAround('akschartsinstall') {
              timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Install Charts to AKS') {
                onPR {
                  deploymentNumber = githubCreateDeployment()
                }

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
    }

    if ((config.deployToAKS || config.installCharts) && config.serviceApp) {
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
}
