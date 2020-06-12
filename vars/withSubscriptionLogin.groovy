#!groovy
/**
 * Simple login to Azure for the correct subscription
 * @param subscription the subscription short name, i.e. sandbox, qa, nonprod, prod
 * @param body the body to execute after logged in
 */
def call(String subscription, Closure body) {
  Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
  az 'login --identity'

  withAzureKeyvault([
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: "${subscription}-subscription-id", version: '', envVariable: 'ARM_SUBSCRIPTION_ID']
  ]) {
    az "account set --subscription ${env.ARM_SUBSCRIPTION_ID}"
    def tenantId = az "account show --query tenantId -o tsv"
    withEnv([
      "SUBSCRIPTION_NAME=$subscription",
      "ARM_TENANT_ID=${tenantId}",
    ]) {
      body.call()
    }
  }
}
