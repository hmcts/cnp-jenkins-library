#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType

def testEnv(String testUrl, tfOutput, block) {
  def testEnvVariables = ["TEST_URL=${testUrl}"]

  for (o in tfOutput) {
    def envVariable = o.key.toUpperCase() + "=" + o.value.value
    echo(envVariable)
    testEnvVariables.add(envVariable)
  }

  withEnv(testEnvVariables) {
    echo "Using TEST_URL: '$env.TEST_URL'"
    block.call()
  }
}

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  def deploymentTarget = params.deploymentTarget
  def envTfOutput = params.envTfOutput
  Long deploymentNumber

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer

  def tfOutput

  def environmentDt = "${environment}${deploymentTarget}"

  //TODO: remove
  echo "INFO: inside main file sectionDeployToDeploymentTarget ${deploymentTarget}"

  if (deploymentTarget != '') {
    stage("Build Infrastructure - ${environmentDt}") {
      onPreview {
        deploymentNumber = githubCreateDeployment()
      }

      folderExists('infrastructure/deploymentTarget') {

      //TODO: remove
      echo "INFO: inside infra directory sectionDeployToDeploymentTarget ${deploymentTarget}"

        withSubscription(subscription) {
          dir('infrastructure/deploymentTarget') {
            pl.callAround("buildinfra:${environmentDt}") {
              timeoutWithMsg(time: 120, unit: 'MINUTES', action: "buildinfra:${environmentDt}") {
                withIlbIp(environmentDt) {
                  def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                  withEnv(additionalInfrastructureVariables) {
                    tfOutput = spinInfra(product, component, environment, false, subscription, deploymentTarget)
                  }
                  scmServiceRegistration(environmentDt)
                }
              }
            }
          }
        }
      }
    }
  }

  // merge env and deployment target tf output using some groovy magic
  def mergedTfOutput
  if (tfOutput) {
    envTfOutput.properties.each {
      tfOutput.metaClass[it.key] = it.value
    }
    mergedTfOutput = tfOutput
  } else {
    mergedTfOutput = envTfOutput
  }


  stage("Deploy - ${environmentDt} (staging slot)") {
    withSubscription(subscription) {
      pl.callAround("deploy:${environmentDt}") {
        timeoutWithMsg(time: 30, unit: 'MINUTES', action: "Deploy - ${environmentDt} (staging slot)") {
          deployer.deploy(environmentDt)
          deployer.healthCheck(environmentDt, "staging")

          onPreview {
            githubUpdateDeploymentStatus(deploymentNumber, deployer.getServiceUrl(environmentDt, "staging"))
          }
        }
      }
    }
  }

  withSubscription(subscription) {
    withTeamSecrets(pl, environment, mergedTfOutput?.vaultUri?.value) {
      stage("Smoke Test - ${environmentDt} (staging slot)") {
        testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
          pl.callAround("smoketest:${environmentDt}-staging") {
            timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'smoke test') {
              builder.smokeTest()
            }
          }
        }
      }

      onFunctionalTestEnvironment(environment) {
        stage("Functional Test - ${environmentDt} (staging slot)") {
          testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
            pl.callAround("functionalTest:${environmentDt}") {
              timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test') {
                builder.functionalTest()
              }
            }
          }
        }
        if (pl.performanceTest) {
          stage("Performance Test - ${environmentDt} (staging slot)") {
            testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
              pl.callAround("performanceTest:${environmentDt}") {
                timeoutWithMsg(time: 120, unit: 'MINUTES', action: "Performance Test - ${environmentDt} (staging slot)") {
                  builder.performanceTest()
                  publishPerformanceReports(this, params)
                }
              }
            }
          }
        }
        if (pl.crossBrowserTest) {
          stage("CrossBrowser Test - ${environmentDt} (staging slot)") {
            testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
              pl.callAround("crossBrowserTest:${environmentDt}") {
                builder.crossBrowserTest()
              }
            }
          }
        }
        if (pl.mutationTest) {
          stage("Mutation Test - ${environmentDt} (staging slot)") {
            testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
              pl.callAround("mutationTest:${environmentDt}") {
                builder.mutationTest()
              }
            }
          }
        }
        if (pl.fullFunctionalTest) {
          stage("FullFunctional Test - ${environmentDt} (staging slot)") {
            testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
              pl.callAround("crossBrowserTest:${environmentDt}") {
                builder.fullFunctionalTest()
              }
            }
          }
        }

      }

      stage("Promote - ${environmentDt} (staging -> production slot)") {
        withSubscription(subscription) {
          pl.callAround("promote:${environmentDt}") {
            timeoutWithMsg(time: 15, unit: 'MINUTES', action: "Promote - ${environmentDt} (staging -> production slot)") {
              sh "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az webapp deployment slot swap --name \"${product}-${component}-${environmentDt}\" --resource-group \"${product}-${component}-${environmentDt}\" --slot staging --target-slot production"
              deployer.healthCheck(environmentDt, "production")

              onPreview {
                githubUpdateDeploymentStatus(deploymentNumber, deployer.getServiceUrl(environmentDt, "production"))
              }
            }
          }
        }
      }

      stage("Smoke Test - ${environmentDt} (production slot)") {
        testEnv(deployer.getServiceUrl(environmentDt, "production"), mergedTfOutput) {
          pl.callAround("smoketest:${environmentDt}") {
            timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke test (prod slot)') {
              builder.smokeTest()
            }
          }
        }
      }

      onNonPR() {
        if (pl.apiGatewayTest) {
          stage("API Gateway Test - ${environmentDt} (production slot)") {
            testEnv(deployer.getServiceUrl(environmentDt, "production"), mergedTfOutput) {
              pl.callAround("apiGatewayTest:${environmentDt}") {
                timeoutWithMsg(time: pl.apiGatewayTestTimeout, unit: 'MINUTES', action: "API Gateway Test - ${environmentDt} (production slot)") {
                  builder.apiGatewayTest()
                }
              }
            }
          }
        }
      }
    }
    milestone()
  }
}
