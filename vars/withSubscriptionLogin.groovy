#!groovy
import uk.gov.hmcts.pipeline.AgentSelector

/**
 * Simple login to Azure for the correct subscription
 * @param subscription the subscription short name, i.e. sandbox, qa, nonprod, prod
 * @param body the body to execute after logged in
 */
def call(String subscription, Closure body) {
  String targetEnvironment = AgentSelector.normaliseEnvironment(subscription)
  if (['dev', 'stg', 'prod', 'sbox'].contains(targetEnvironment)) {
    String agentLabel = "ubuntu-${targetEnvironment}"
    String product = env.PRODUCT ?: env.RAW_PRODUCT_NAME ?: ''
    if (env.BUILD_AGENT_TYPE != agentLabel) {
      withEnvironmentAgent(targetEnvironment, product, agentLabel) {
        loginOnCurrentAgent(subscription, body)
      }
      return
    }
  }

  loginOnCurrentAgent(subscription, body)
}

def loginOnCurrentAgent(String subscription, Closure body) {
  Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

  if (env.SUBSCRIPTION_NAME == subscription && fileExists("/opt/jenkins/.azure-${subscription}")) {
    echo "Refreshing existing az login: /opt/jenkins/.azure-${subscription}"
  } else {
    echo "New az login: /opt/jenkins/.azure-${subscription}"
  }

  az 'login --identity'

  withAzureKeyvault([
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: "${subscription}-subscription-id", version: '', envVariable: 'ARM_SUBSCRIPTION_ID']
  ]) {
    sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az account set --subscription \$ARM_SUBSCRIPTION_ID", returnStdout: true)
    def tenantId = az "account show --query tenantId -o tsv"
    env.SUBSCRIPTION_NAME = subscription
    env.ARM_TENANT_ID = tenantId
    env.CURRENT_ARM_SUBSCRIPTION_ID = env.ARM_SUBSCRIPTION_ID
    body.call()
  }
}
