#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch

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
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  def deploymentTarget = params.deploymentTarget
  def envTfOutput = params.envTfOutput
  def deploymentNumber = params.deploymentNumber
  def pactBrokerUrl = params.pactBrokerUrl

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer

  def tfOutput

  def environmentDt = "${environment}${deploymentTarget}"

  if (deploymentTarget != '') {
    stage("Build Infrastructure - ${environmentDt}") {

      folderExists('infrastructure/deploymentTarget') {

        withSubscription(subscription) {
          dir('infrastructure/deploymentTarget') {
            pcr.callAround("buildinfra:${environmentDt}") {
              timeoutWithMsg(time: 120, unit: 'MINUTES', action: "buildinfra:${environmentDt}") {
                def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                withEnv(additionalInfrastructureVariables) {
                  tfOutput = spinInfra(product, component, environment, false, subscription, deploymentTarget)
                }
              }
            }
          }
        }
      }
    }
  }

   if (config.legacyDeploymentForEnv(environment)) {
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
        pcr.callAround("deploy:${environmentDt}") {
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
      withTeamSecrets(config, environment) {
        stage("Smoke Test - ${environmentDt} (staging slot)") {
          testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
            pcr.callAround("smoketest:${environmentDt}-staging") {
              timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'smoke test') {
                builder.smokeTest()
              }
            }
          }
        }

        onFunctionalTestEnvironment(environment) {
          stage("Functional Test - ${environmentDt} (staging slot)") {
            testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
              pcr.callAround("functionalTest:${environmentDt}") {
                timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test') {
                  builder.functionalTest()
                }
              }
            }
          }
          if (config.performanceTest) {
            stage("Performance Test - ${environmentDt} (staging slot)") {
              testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
                pcr.callAround("performanceTest:${environmentDt}") {
                  timeoutWithMsg(time: 120, unit: 'MINUTES', action: "Performance Test - ${environmentDt} (staging slot)") {
                    builder.performanceTest()
                    publishPerformanceReports(this, params)
                  }
                }
              }
            }
          }
          if (config.crossBrowserTest) {
            stage("CrossBrowser Test - ${environmentDt} (staging slot)") {
              testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
                pcr.callAround("crossBrowserTest:${environmentDt}") {
                  builder.crossBrowserTest()
                }
              }
            }
          }
          if (config.mutationTest) {
            stage("Mutation Test - ${environmentDt} (staging slot)") {
              testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
                pcr.callAround("mutationTest:${environmentDt}") {
                  builder.mutationTest()
                }
              }
            }
          }
          if (config.fullFunctionalTest) {
            stage("FullFunctional Test - ${environmentDt} (staging slot)") {
              testEnv(deployer.getServiceUrl(environmentDt, "staging"), mergedTfOutput) {
                pcr.callAround("crossBrowserTest:${environmentDt}") {
                  builder.fullFunctionalTest()
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
        }

        stage("Promote - ${environmentDt} (staging -> production slot)") {
          withSubscription(subscription) {
            pcr.callAround("promote:${environmentDt}") {
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
            pcr.callAround("smoketest:${environmentDt}") {
              timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke test (prod slot)') {
                builder.smokeTest()
              }
            }
          }
        }

        onNonPR() {
          if (config.apiGatewayTest) {
            stage("API Gateway Test - ${environmentDt} (production slot)") {
              testEnv(deployer.getServiceUrl(environmentDt, "production"), mergedTfOutput) {
                pcr.callAround("apiGatewayTest:${environmentDt}") {
                  timeoutWithMsg(time: config.apiGatewayTestTimeout, unit: 'MINUTES', action: "API Gateway Test - ${environmentDt} (production slot)") {
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
}
