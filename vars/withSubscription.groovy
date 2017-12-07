#!groovy
import groovy.json.JsonSlurperClassic

def call(String env, Closure body) {
  withCredentials([azureServicePrincipal(
    credentialsId: "jenkinsServicePrincipal",
    subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
    clientIdVariable: 'JENKINS_CLIENT_ID',
    clientSecretVariable: 'JENKINS_CLIENT_SECRET',
    tenantIdVariable: 'JENKINS_TENANT_ID')]) {

    ansiColor('xterm') {

      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'

      def cred_by_env_name = (env == 'prod') ? "prod-creds" : "nonprod-creds"
      def resp = sh(script: "az keyvault secret show --vault-name 'infra-vault' --name '$cred_by_env_name'", returnStdout: true).trim()
      secrets = new JsonSlurperClassic().parseText(resp)
      echo "=== you are building with $cred_by_env_name subscription credentials ==="
      //echo "TOKEN: '${secrets}'; Type: ${secrets.getClass()}"

      values = new JsonSlurperClassic().parseText(secrets.value)
      //echo "Values: '${values}'; Type: ${values.getClass()}"

      withEnv(["AZURE_CLIENT_ID=${values.azure_client_id}",
               "AZURE_CLIENT_SECRET=${values.azure_client_secret}",
               "AZURE_TENANT_ID=${values.azure_tenant_id}",
               "AZURE_SUBSCRIPTION_ID=${values.azure_subscription}",
               // Terraform env variables
               "ARM_CLIENT_ID=${values.azure_client_id}",
               "ARM_CLIENT_SECRET=${values.azure_client_secret}",
               "ARM_TENANT_ID=${values.azure_tenant_id}",
               "ARM_SUBSCRIPTION_ID=${values.azure_subscription}",
               // Terraform input variables
               "TF_VAR_client_id=${values.azure_client_id}",
               "TF_VAR_secret_access_key=${values.azure_client_secret}",
               "TF_VAR_tenant_id=${values.azure_tenant_id}",
               "TF_VAR_subscription_id=${values.azure_subscription}",
               "TF_VAR_token=${values.azure_tenant_id}",
               // other variables
               "TOKEN=${values.azure_tenant_id}"]) {

        echo "Setting Azure CLI to run on $cred_by_env_name subscription"
        sh "az login --service-principal -u $ARM_CLIENT_ID -p $ARM_CLIENT_SECRET -t $ARM_TENANT_ID"
        sh "az account set --subscription $ARM_SUBSCRIPTION_ID"

        body.call()
      }
    }
  }
}
