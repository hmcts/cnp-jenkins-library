#!groovy
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.PipelineCallbacks

def testEnv(String testUrl, tfOutput, block) {
  withEnv(
    [
      "TEST_URL=${testUrl}",
      "IDAM_API_URL=${tfOutput?.idam_api_url?.value}",
      "S2S_URL=${tfOutput?.s2s_url?.value}",
    ]) {
    echo "Using TEST_URL: '$TEST_URL'"
    echo "Using IDAM_API_URL: '$IDAM_API_URL'"
    echo "Using S2S_URL: '$S2S_URL'"
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

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer

  stage("Build Infrastructure - ${environment}") {
    folderExists('infrastructure') {
      withSubscription(subscription) {
        dir('infrastructure') {
          pl.callAround("buildinfra:${environment}") {
            withIlbIp(environment) {
              tfOutput = spinInfra("${product}-${component}", environment, false, subscription)
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
      }
    }
  }

  withSubscription(subscription) {
    wrap([
      $class              : 'AzureKeyVaultBuildWrapper',
      azureKeyVaultSecrets: pl.vaultSecrets,
      keyVaultURLOverride : tfOutput?.vaultUri?.value,
      applicationIDOverride: env.AZURE_CLIENT_ID,
      applicationSecretOverride: env.AZURE_CLIENT_SECRET
    ]) {
      stage("Smoke Test - ${environment} (staging slot)") {
        testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
          pl.callAround("smoketest:${environment}") {
            builder.smokeTest()
          }
        }
      }

      onAATEnvironment(environment) {
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
  }
}
