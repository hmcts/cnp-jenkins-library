#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, Closure body) {
  ansiColor('xterm') {
    withCredentials([azureServicePrincipal(
      credentialsId: "jenkinsServicePrincipal",
      subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
      clientIdVariable: 'JENKINS_CLIENT_ID',
      clientSecretVariable: 'JENKINS_CLIENT_SECRET',
      tenantIdVariable: 'JENKINS_TENANT_ID')]) {

      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'

      def vaultName = "infra-vault"
      if (subscription == "sandbox")
        vaultName = "contino-devops"

      def subscriptionCredsjson = sh(script: "az keyvault secret show --vault-name '$vaultName' --name '$subscription-creds' --query value -o tsv", returnStdout: true).trim()
      subscriptionCredValues = new JsonSlurperClassic().parseText(subscriptionCredsjson)
      echo "$subscriptionCredValues"

      def stateStoreCfgjson = sh(script: "az keyvault secret show --vault-name '$vaultName' --name 'cfg-state-store' --query value -o tsv", returnStdout: true).trim()
      stateStoreCfgValues = new JsonSlurperClassic().parseText(stateStoreCfgjson)
      echo "$subscriptionCredValues"

      log.warning "=== you are building with $subscription subscription credentials ==="

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
               "TF_VAR_token=${subscriptionCredValues.azure_tenant_id}",
               // other variables
               "TOKEN=${subscriptionCredValues.azure_tenant_id}",
               "STORE_rg_name_template='${stateStoreCfgValues.rg_name}'",
               "STORE_sa_name_template='${stateStoreCfgValues.sa_name}'",
               "STORE_sa_container_name_template='${stateStoreCfgValues.sa_container_name}'"]) {

        echo "Setting Azure CLI to run on $subscription subscription account"
        sh "az login --service-principal -u $subscriptionCredValues.azure_client_id -p $subscriptionCredValues.azure_client_secret -t $subscriptionCredValues.azure_tenant_id"
        sh "az account set --subscription $subscriptionCredValues.azure_subscription_id"

        sh 'env|grep "TF_VAR\\|AZURE\\|ARM\\|STORE"'

        body.call()
      }
    }
  }
}
