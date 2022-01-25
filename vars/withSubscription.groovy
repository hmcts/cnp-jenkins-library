#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, Closure body) {
  identityBasedLogin(subscription, body)
}

def identityBasedLogin(String subscription, Closure body) {
  ansiColor('xterm') {
    Closure azJenkins = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
    azJenkins "account set --subscription ${env.JENKINS_SUBSCRIPTION_NAME}"
    def mgmtSubscriptionId = azJenkins 'account show --query id -o tsv'

    withSubscriptionLogin(subscription) {
      def infraVaultName = env.INFRA_VAULT_NAME
      log.info "Using $infraVaultName"

      log.warning "=== you are building with $subscription subscription credentials ==="

      def jenkinsObjectId = azJenkins "identity show -g managed-identities-${infraVaultName}-rg --name jenkins-${infraVaultName}-mi --query principalId -o tsv"

      def tfStateRgNameTemplate = env.TF_STATE_RG_TEMPLATE ?: "mgmt-state-store"
      def tfStateStorageAccountNameTemplate = env.TF_STATE_STORAGE_TEMPLATE ?: "mgmtstatestore"
      def tfStateContainerNameTemplate = env.TF_STATE_CONTAINER_TEMPLATE ?: "mgmtstatestorecontainer"
      def rootAddressSpace = env.ROOT_ADDRESS_SPACE ?: "10.96.0.0/12"

      Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
      def storageAccountKey = az "storage account keys list --account-name ${tfStateStorageAccountNameTemplate}${subscription} --query [0].value -o tsv"
      def tenantId = az "account show --query tenantId -o tsv"

      withEnv([
        "ARM_USE_MSI=true",
        // Terraform env variables
        "ARM_ACCESS_KEY=${storageAccountKey}",
        // Terraform input variables
        "TF_VAR_tenant_id=${tenantId}",
        "TF_VAR_subscription_id=${env.ARM_SUBSCRIPTION_ID}",
        "TF_VAR_mgmt_subscription_id=${mgmtSubscriptionId}",
        "TF_VAR_token=${tenantId}",
        // other variables
        "STORE_rg_name_template=${tfStateRgNameTemplate}",
        "STORE_sa_name_template=${tfStateStorageAccountNameTemplate}",
        "STORE_sa_container_name_template=${tfStateContainerNameTemplate}",
        "TF_VAR_jenkins_AAD_objectId=${jenkinsObjectId}",
        "TF_VAR_root_address_space=${rootAddressSpace}",
        "INFRA_VAULT_URL=https://${infraVaultName}.vault.azure.net/"
      ])
        {
          body.call()
        }
    }
  }
}
