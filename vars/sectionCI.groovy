import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.PipelineCallbacks
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
  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def aksUrl

  Builder builder = pipelineType.builder

  if (pl.dockerBuild) {
    withAksClient(subscription) {
      def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
      def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag())

      stage('Docker Build') {
        pl.callAround('dockerbuild') {
          timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Docker build') {
            acr.build(dockerImage)
          }
        }
      }

      onPR {
        if (pl.deployToAKS) {
          withTeamSecrets(pl, params.environment) {
            stage('Deploy to AKS') {
              pl.callAround('aksdeploy') {
                timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Deploy to AKS') {
                  deploymentNumber = githubCreateDeployment()

                  aksUrl = aksDeploy(dockerImage, params)
                  log.info("deployed component URL: ${aksUrl}")

                  githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
                }
              }
            }
          }
        } else if (pl.installCharts) {
          withTeamSecrets(pl, params.environment) {
            stage('Install Charts to AKS') {
              pl.callAround('akschartsinstall') {
                timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Install Charts to AKS') {
                  deploymentNumber = githubCreateDeployment()

                  aksUrl = helmInstall(dockerImage, params)
                  log.info("deployed component URL: ${aksUrl}")

                  githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
                }
              }
            }
          }
        }
      }
    }

    onPR {
      if (pl.deployToAKS || pl.installCharts) {
        withSubscription(subscription) {
          withTeamSecrets(pl, params.environment) {
            stage("Smoke Test - AKS") {
              testEnv(aksUrl) {
                pl.callAround("smoketest:aks") {
                  timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke Test - AKS') {
                    builder.smokeTest()
                  }
                }
              }
            }

            def environment = subscription == 'nonprod' ? 'preview' : 'saat'

            onFunctionalTestEnvironment(environment) {
              stage("Functional Test - AKS") {
                testEnv(aksUrl) {
                  pl.callAround("functionalTest:${environment}") {
                    timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test - AKS') {
                      builder.functionalTest()
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    
    
    onHMCTSDemo {
        if (pl.deployToAKS) {
          withTeamSecrets(pl, params.environment) {
            stage('Deploy to AKS') {
              pl.callAround('aksdeploy') {
                timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Deploy to AKS') {
                  deploymentNumber = githubCreateDeployment()

                  aksUrl = aksDeploy(dockerImage, params)
                  log.info("deployed component URL: ${aksUrl}")

                  githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
                }
              }
            }
          }
        } else if (pl.installCharts) {
          withTeamSecrets(pl, params.environment) {
            stage('Install Charts to AKS') {
              pl.callAround('akschartsinstall') {
                timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Install Charts to AKS') {
                  deploymentNumber = githubCreateDeployment()

                  aksUrl = helmInstall(dockerImage, params)
                  log.info("deployed component URL: ${aksUrl}")

                  githubUpdateDeploymentStatus(deploymentNumber, aksUrl)
                }
              }
            }
          }
        }
      }
    }

    onHMCTSDemo {
      if (pl.deployToAKS || pl.installCharts) {
        withSubscription(subscription) {
          withTeamSecrets(pl, params.environment) {
            stage("Smoke Test - AKS") {
              testEnv(aksUrl) {
                pl.callAround("smoketest:aks") {
                  timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke Test - AKS') {
                    builder.smokeTest()
                  }
                }
              }
            }

            def environment = subscription == 'nonprod' ? 'preview' : 'saat'

            onFunctionalTestEnvironment(environment) {
              stage("Functional Test - AKS") {
                testEnv(aksUrl) {
                  pl.callAround("functionalTest:${environment}") {
                    timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test - AKS') {
                      builder.functionalTest()
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
