#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, Closure body) {

  ansiColor('xterm') {
    withCredentials([azureServicePrincipal(
      credentialsId: "jenkinsServicePrincipal",
      subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
      clientIdVariable: 'JENKINS_CLIENT_ID',
      clientSecretVariable: 'JENKINS_CLIENT_SECRET',
      tenantIdVariable: 'ARM_TENANT_ID')]) {

      def azJenkins = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
      azJenkins 'login --identity'

      def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
      az 'login --identity'

      def infraVaultName = env.INFRA_VAULT_NAME
      log.info "using $infraVaultName"

      log.warning "=== you are building with $subscription subscription credentials ==="

      def jenkinsObjectId = az "identity show -g managed-identities-cftsbox-intsvc-rg --name jenkins-cftsbox-intsvc-mi --query principalId -o tsv"

      def storageAccountKey = az "storage account keys  list --account-name mgmtstatestore${subscription} --query [0].value -o tsv"
      withEnv([
        "ARM_USE_MSI=true",
//               "AZURE_CLIENT_ID=${subscriptionCredValues.azure_client_id}",
//               "AZURE_CLIENT_SECRET=${subscriptionCredValues.azure_client_secret}",
               // Terraform env variables
//               "ARM_CLIENT_ID=${subscriptionCredValues.azure_client_id}",
//               "ARM_CLIENT_SECRET=${subscriptionCredValues.azure_client_secret}",
               "ARM_SUBSCRIPTION_ID=bf308a5c-0624-4334-8ff8-8dca9fd43783", // TODO update
        "ARM_ACCESS_KEY=${storageAccountKey}",
               // Terraform input variables
               "TF_VAR_tenant_id=${env.ARM_TENANT_ID}",
               "TF_VAR_subscription_id=bf308a5c-0624-4334-8ff8-8dca9fd43783", // TODO update
               "TF_VAR_mgmt_subscription_id=${env.JENKINS_SUBSCRIPTION_ID}",
               "TF_VAR_token=${env.ARM_TENANT_ID}",
               // other variables
               "STORE_rg_name_template=mgmt-state-store",
               "STORE_sa_name_template=mgmtstatestore",
               "STORE_sa_container_name_template=mgmtstatestorecontainer",
               "SUBSCRIPTION_NAME=$subscription",
               "TF_VAR_jenkins_AAD_objectId=${jenkinsObjectId}", // TODO update
               "TF_VAR_root_address_space=10.96.0.0/12",
               "INFRA_VAULT_URL=https://${infraVaultName}.vault.azure.net/"])
      {
        echo "Setting Azure CLI to run on $subscription subscription account"
        az 'account set --subscription bf308a5c-0624-4334-8ff8-8dca9fd43783' // TODO update

        body.call()
      }
    }
  }
}
