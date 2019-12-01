#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, Closure body) {

  ansiColor('xterm') {
    def azJenkins = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
    azJenkins 'login --identity'

    def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
    az 'login --identity'
    az 'account set --subscription bf308a5c-0624-4334-8ff8-8dca9fd43783' // TODO update

    def infraVaultName = env.INFRA_VAULT_NAME
    log.info "using $infraVaultName"

    log.warning "=== you are building with $subscription subscription credentials ==="

    def jenkinsObjectId = azJenkins "identity show -g managed-identities-cftsbox-intsvc-rg --name jenkins-cftsbox-intsvc-mi --query principalId -o tsv"

    def storageAccountKey = az "storage account keys  list --account-name mgmtstatestore${subscription} --query [0].value -o tsv"
    def tenantId = az "account show --query tenantId -o tsv"
    def mgmtSubscriptionId = "1497c3d7-ab6d-4bb7-8a10-b51d03189ee3"
    def subscriptionId = "bf308a5c-0624-4334-8ff8-8dca9fd43783"
    def rootAddressSpace = "10.96.0.0/12"
    withEnv([
      "ARM_USE_MSI=true",
      // Terraform env variables
      "ARM_SUBSCRIPTION_ID=${subscriptionId}",
      "ARM_ACCESS_KEY=${storageAccountKey}",
      "ARM_TENANT_ID=${tenantId}",
      // Terraform input variables
      "TF_VAR_tenant_id=${tenantId}",
      "TF_VAR_subscription_id=${subscriptionId}",
      "TF_VAR_mgmt_subscription_id=${mgmtSubscriptionId}",
      "TF_VAR_token=${tenantId}",
      // other variables
      "STORE_rg_name_template=mgmt-state-store",
      "STORE_sa_name_template=mgmtstatestore",
      "STORE_sa_container_name_template=mgmtstatestorecontainer",
      "SUBSCRIPTION_NAME=$subscription",
      "TF_VAR_jenkins_AAD_objectId=${jenkinsObjectId}",
      "TF_VAR_root_address_space=${rootAddressSpace}",
      "INFRA_VAULT_URL=https://${infraVaultName}.vault.azure.net/"
    ])
      {
        body.call()
      }
  }
}
