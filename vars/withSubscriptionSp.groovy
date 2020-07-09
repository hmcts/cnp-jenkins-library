#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, Closure body) {
      echo "...Managed Identity login not supported, falling back to Service Principal login"
      echo "Error above can safely be ignored"
      servicePrincipalBasedLogin(subscription, body)
}

def servicePrincipalBasedLogin(String subscription, Closure body) {
  ansiColor('xterm') {
    withCredentials([azureServicePrincipal(
      credentialsId: "jenkinsServicePrincipal",
      subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
      clientIdVariable: 'JENKINS_CLIENT_ID',
      clientSecretVariable: 'JENKINS_CLIENT_SECRET',
      tenantIdVariable: 'JENKINS_TENANT_ID')]) {

      def azJenkins = { cmd -> return sh(label: "az $cmd", script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }

      def az = { cmd -> return sh(label: "az $cmd", script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

      azJenkins 'login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      azJenkins 'account set --subscription $JENKINS_SUBSCRIPTION_ID'

      def infraVaultName = "infra-vault-$subscription"
      echo "Using " + infraVaultName

      def subscriptionCredsjson = azJenkins "keyvault secret show --vault-name '$infraVaultName' --name '$subscription-creds' --query value -o tsv".toString()
      subscriptionCredValues = new JsonSlurperClassic().parseText(subscriptionCredsjson)

      def stateStoreCfgjson = azJenkins "keyvault secret show --vault-name '$infraVaultName' --name 'cfg-state-store' --query value -o tsv".toString()
      stateStoreCfgValues = new JsonSlurperClassic().parseText(stateStoreCfgjson)

      def root_address_space = azJenkins "keyvault secret show --vault-name '$infraVaultName' --name 'cfg-root-vnet-cidr' --query value -o tsv".toString()
      def dcdJenkinsObjectId = azJenkins "keyvault secret show --vault-name '$infraVaultName' --name '$subscription-jenkins-object-id' --query value -o tsv".toString()

      echo "=== You are building with $subscription subscription credentials ==="

      withEnv(["AZURE_CLIENT_ID=${subscriptionCredValues.azure_client_id}",
               "AZURE_CLIENT_SECRET=${subscriptionCredValues.azure_client_secret}",
               "AZURE_TENANT_ID=${subscriptionCredValues.azure_tenant_id}",
               "AZURE_SUBSCRIPTION_ID=${subscriptionCredValues.azure_subscription}",
               // Terraform env variables
               "ARM_CLIENT_ID=${subscriptionCredValues.azure_client_id}",
               "ARM_CLIENT_SECRET=${subscriptionCredValues.azure_client_secret}",
               "ARM_TENANT_ID=${subscriptionCredValues.azure_tenant_id}",
               "ARM_SUBSCRIPTION_ID=${subscriptionCredValues.azure_subscription}",
               // Terraform input variables
               "TF_VAR_client_id=${subscriptionCredValues.azure_client_id}",
               "TF_VAR_secret_access_key=${subscriptionCredValues.azure_client_secret}",
               "TF_VAR_tenant_id=${subscriptionCredValues.azure_tenant_id}",
               "TF_VAR_subscription_id=${subscriptionCredValues.azure_subscription}",
               "TF_VAR_mgmt_subscription_id=${env.JENKINS_SUBSCRIPTION_ID}",
               "TF_VAR_token=${subscriptionCredValues.azure_tenant_id}",
               // other variables
               "TOKEN=${subscriptionCredValues.azure_tenant_id}",
               "STORE_rg_name_template=${stateStoreCfgValues.rg_name}",
               "STORE_sa_name_template=${stateStoreCfgValues.sa_name}",
               "STORE_sa_container_name_template=${stateStoreCfgValues.sa_container_name}",
               "SUBSCRIPTION_NAME=$subscription",
               "TF_VAR_jenkins_AAD_objectId=$dcdJenkinsObjectId",
               "TF_VAR_root_address_space=$root_address_space",
               "INFRA_VAULT_URL=https://${infraVaultName}.vault.azure.net/"])
        {
          echo "Setting Azure CLI to run on $subscription subscription account"
          az 'login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET -t $AZURE_TENANT_ID'
          az 'account set --subscription $AZURE_SUBSCRIPTION_ID'

          body.call()
        }
    }
  }
} 
