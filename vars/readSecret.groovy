#!groovy
import groovy.json.JsonSlurperClassic

def call(String secretName) {

  echo 'Attempting secret read ... current subscription: $ARM_SUBSCRIPTION_ID'

  withCredentials([azureServicePrincipal(
    credentialsId: "jenkinsServicePrincipal",
    subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
    clientIdVariable: 'JENKINS_CLIENT_ID',
    clientSecretVariable: 'JENKINS_CLIENT_SECRET',
    tenantIdVariable: 'JENKINS_TENANT_ID')])
    {
      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'

      def secret = sh(script: "az keyvault secret show --vault-name 'infra-vault' --name '$secretName'", returnStdout: true).trim()
      parsedSecret = new JsonSlurperClassic().parseText(secret)
      echo "$parsedSecret"

      return new JsonSlurperClassic().parseText(parsedSecret.value)
    }

  echo "Setting subscription back to $env.ARM_SUBSCRIPTION_ID for Azure CLI"
  sh 'az login --service-principal -u $ARM_CLIENT_ID -p $ARM_CLIENT_SECRET -t $ARM_TENANT_ID'
  sh 'az account set --subscription $ARM_SUBSCRIPTION_ID'

}
