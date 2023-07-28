#!groovy
/**
 * Simple login to Azure for the correct subscription
 * @param subscription the subscription short name, i.e. sandbox, qa, nonprod, prod
 * @param body the body to execute after logged in
 */
def call(String subscription, Closure body) {
  echo "New az login: /opt/jenkins/.azure-${subscription}"
  Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
  az 'login --identity'

  withAzureKeyvault([
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: "${subscription}-subscription-id", version: '', envVariable: 'ARM_SUBSCRIPTION_ID']
  ]) {
    az "account set --subscription ${env.ARM_SUBSCRIPTION_ID}"
    def tenantId = az "account show --query tenantId -o tsv"
    env.SUBSCRIPTION_NAME = subscription
    env.ARM_TENANT_ID = tenantId
    env.CURRENT_ARM_SUBSCRIPTION_ID = env.ARM_SUBSCRIPTION_ID
    body.call()
  }
}

def call(String subscription, Closure body) {
  call(subscription, false, body)
}
