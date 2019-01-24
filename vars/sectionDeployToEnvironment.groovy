#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

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

def collectAdditionalInfrastructureVariablesFor(subscription, product, environment) {
  KeyVault keyVault = new KeyVault(this, subscription, "${product}-${environment}")
  def environmentVariables = []

  def appInsightsInstrumentationKey = keyVault.find(ProductVaultEntries.APP_INSIGHTS_INSTRUMENTATION_KEY)
  if (appInsightsInstrumentationKey) {
    environmentVariables.add("TF_VAR_appinsights_instrumentation_key=${appInsightsInstrumentationKey}")
  }

  return environmentVariables
}

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  Long deploymentNumber

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer

  lock(resource: "${product}-${component}-${environment}-deploy", inversePrecedence: true) {
    stage("Build Infrastructure - ${environment}") {
      onPreview {
        deploymentNumber = githubCreateDeployment()
      }

      folderExists('infrastructure') {
        withSubscription(subscription) {
          dir('infrastructure') {
            pl.callAround("buildinfra:${environment}") {
              timeoutWithMsg(time: 120, unit: 'MINUTES', action: "buildinfra:${environment}") {
                withIlbIp(environment) {
                  def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                  withEnv(additionalInfrastructureVariables) {
                    tfOutput = spinInfra(product, component, environment, false, subscription)
                  }
                  scmServiceRegistration(environment)
                }
              }
            }
          }
          if (pl.migrateDb) {
            stage("DB Migration - ${environment}") {
              pl.callAround("dbmigrate:${environment}") {
                builder.dbMigrate(tfOutput.vaultName.value, tfOutput.microserviceName.value)
              }
            }
          }
        }
      }
    }

    stage("Deploy - ${environment} (staging slot)") {
      withSubscription(subscription) {
        pl.callAround("deploy:${environment}") {
          timeoutWithMsg(time: 30, unit: 'MINUTES', action: "Deploy - ${environment} (staging slot)") {
            deployer.deploy(environment)
            deployer.healthCheck(environment, "staging")

            onPreview {
              githubUpdateDeploymentStatus(deploymentNumber, deployer.getServiceUrl(environment, "staging"))
            }
          }
        }
      }
    }

    withSubscription(subscription) {
      withTeamSecrets(pl, environment, tfOutput?.vaultUri?.value) {
        stage("Smoke Test - ${environment} (staging slot)") {
          testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
            pl.callAround("smoketest:${environment}-staging") {
              timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'smoke test') {
                builder.smokeTest()
              }
            }
          }
        }

        onFunctionalTestEnvironment(environment) {
          stage("Functional Test - ${environment} (staging slot)") {
            testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
              pl.callAround("functionalTest:${environment}") {
                timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Functional Test') {
                  builder.functionalTest()
                }
              }
            }
          }
          if (pl.performanceTest) {
            stage("Performance Test - ${environment} (staging slot)") {
              testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
                pl.callAround("performanceTest:${environment}") {
                  timeoutWithMsg(time: 120, unit: 'MINUTES', action: "Performance Test - ${environment} (staging slot)") {
                    builder.performanceTest()
                    publishPerformanceReports(this, params)
                  }
                }
              }
            }
          }
          if (pl.crossBrowserTest) {
            stage("CrossBrowser Test - ${environment} (staging slot)") {
              testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
                pl.callAround("crossBrowserTest:${environment}") {
                  builder.crossBrowserTest()
                }
              }
            }
          }
          if (pl.mutationTest) {
            stage("Mutation Test - ${environment} (staging slot)") {
              testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
                pl.callAround("mutationTest:${environment}") {
                  builder.mutationTest()
                }
              }
            }
          }
          if (pl.fullFunctionalTest) {
            stage("FullFunctional Test - ${environment} (staging slot)") {
              testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
                pl.callAround("crossBrowserTest:${environment}") {
                  builder.fullFunctionalTest()
                }
              }
            }
          }

        }

        stage("Promote - ${environment} (staging -> production slot)") {
          withSubscription(subscription) {
            pl.callAround("promote:${environment}") {
              timeoutWithMsg(time: 15, unit: 'MINUTES', action: "Promote - ${environment} (staging -> production slot)") {
                sh "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az webapp deployment slot swap --name \"${product}-${component}-${environment}\" --resource-group \"${product}-${component}-${environment}\" --slot staging --target-slot production"
                deployer.healthCheck(environment, "production")

                onPreview {
                  githubUpdateDeploymentStatus(deploymentNumber, deployer.getServiceUrl(environment, "production"))
                }
              }
            }
          }
        }

        stage("Smoke Test - ${environment} (production slot)") {
          testEnv(deployer.getServiceUrl(environment, "production"), tfOutput) {
            pl.callAround("smoketest:${environment}") {
              timeoutWithMsg(time: 10, unit: 'MINUTES', action: 'Smoke test (prod slot)') {
                builder.smokeTest()
              }
            }
          }
        }

        onNonPR() {
          if (pl.apiGatewayTest) {
            stage("API Gateway Test - ${environment} (production slot)") {
              testEnv(deployer.getServiceUrl(environment, "production"), tfOutput) {
                pl.callAround("apiGatewayTest:${environment}") {
                  timeoutWithMsg(time: pl.apiGatewayTestTimeout, unit: 'MINUTES', action: "API Gateway Test - ${environment} (production slot)") {
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
