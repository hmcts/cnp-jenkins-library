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

  milestone()
  lock(resource: "${product}-${component}-${environment}", inversePrecedence: true) {
    stage("Build Infrastructure - ${environment}") {
      onPR {
        deploymentNumber = githubCreateDeployment()
      }

      folderExists('infrastructure') {
        withSubscription(subscription) {
          dir('infrastructure') {
            pl.callAround("buildinfra:${environment}") {
              withIlbIp(environment) {
                def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                withEnv(additionalInfrastructureVariables) {
                  tfOutput = spinInfra(product, component, environment, false, subscription)
                }
                scmServiceRegistration(environment)
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
          deployer.deploy(environment)
          deployer.healthCheck(environment, "staging")

          onPR {
            githubUpdateDeploymentStatus(deploymentNumber, deployer.getServiceUrl(environment, "staging"))
          }
        }
      }
    }

    withSubscription(subscription) {
      wrap([
        $class                   : 'AzureKeyVaultBuildWrapper',
        azureKeyVaultSecrets     : pl.vaultSecrets,
        keyVaultURLOverride      : tfOutput?.vaultUri?.value,
        applicationIDOverride    : env.AZURE_CLIENT_ID,
        applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {
        stage("Smoke Test - ${environment} (staging slot)") {
          testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
            pl.callAround("smoketest:${environment}") {
              builder.smokeTest()
            }
          }
        }

        onFunctionalTestEnvironment(environment) {
          stage("Functional Test - ${environment} (staging slot)") {
            testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
              pl.callAround("functionalTest:${environment}") {
                builder.functionalTest()
              }
            }
          }
        }

        stage("Promote - ${environment} (staging -> production slot)") {
          withSubscription(subscription) {
            pl.callAround("promote:${environment}") {
              sh "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az webapp deployment slot swap --name \"${product}-${component}-${environment}\" --resource-group \"${product}-${component}-${environment}\" --slot staging --target-slot production"
              deployer.healthCheck(environment, "production")

              onPR {
                githubUpdateDeploymentStatus(deploymentNumber, deployer.getServiceUrl(environment, "production"))
              }
            }
          }
        }

        stage("Smoke Test - ${environment} (production slot)") {
          testEnv(deployer.getServiceUrl(environment, "production"), tfOutput) {
            pl.callAround("smokeTest:${environment}") {
              builder.smokeTest()
            }
          }
        }
      }
      milestone()
    }
  }
}
