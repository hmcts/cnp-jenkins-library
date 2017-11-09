#!groovy
import groovy.json.JsonSlurperClassic

def call(String servicePrincipal, String vaultName, Closure body) {

  withCredentials([azureServicePrincipal(
    credentialsId: servicePrincipal,
    subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
    clientIdVariable: 'JENKINS_CLIENT_ID',
    clientSecretVariable: 'JENKINS_CLIENT_SECRET',
    tenantIdVariable: 'JENKINS_TENANT_ID')])
    {
      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'

      def resp = steps.sh(script: "az keyvault secret show --vault-name '$vaultName' --name 'terraform-creds'", returnStdout: true).trim()
      secrets = new JsonSlurperClassic().parseText(resp)
      echo "TOKEN: '${secrets}'; Type: ${secrets.getClass()}"

      values = new JsonSlurperClassic().parseText(secrets.value)
      echo "Values: '${values}'; Type: ${values.getClass()}"

      env.AZURE_CLIENT_ID = values.azure_client_id
      env.AZURE_CLIENT_SECRET = values.azure_client_secret
      env.AZURE_TENANT_ID = values.azure_tenant_id
      env.AZURE_SUBSCRIPTION_ID = values.azure_subscription
      // Terraform env variables
      env.ARM_CLIENT_ID = values.azure_client_id
      env.ARM_CLIENT_SECRET = values.azure_client_secret
      env.ARM_TENANT_ID = values.azure_tenant_id
      env.ARM_SUBSCRIPTION_ID = values.azure_subscription

      env.TOKEN = env.ARM_TENANT_ID
      env.TF_VAR_token = env.ARM_TENANT_ID

      env.TF_VAR_secret_access_key = env.ARM_CLIENT_SECRET
      env.TF_VAR_tenant_id = env.ARM_TENANT_ID
      env.TF_VAR_subscription_id = env.ARM_SUBSCRIPTION_ID
      env.TF_VAR_client_id = env.ARM_CLIENT_ID

      echo "$env"

      body.call()
    }
}
