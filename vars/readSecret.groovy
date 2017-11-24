#!groovy
import groovy.json.JsonSlurper

def call(String secretName) {

  withCredentials([azureServicePrincipal(
    credentialsId: "jenkinsServicePrincipal",
    subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
    clientIdVariable: 'JENKINS_CLIENT_ID',
    clientSecretVariable: 'JENKINS_CLIENT_SECRET',
    tenantIdVariable: 'JENKINS_TENANT_ID')])
    {
      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'

      def secret = steps.sh(script: "az keyvault secret show --vault-name 'infra-vault' --name '$secretName'", returnStdout: true).trim()
      parsedSecret = new JsonSlurper().parseText(secret)
      echo "$parsedSecret"

      return new JsonSlurper().parseText(parsedSecret.value)
    }
}
