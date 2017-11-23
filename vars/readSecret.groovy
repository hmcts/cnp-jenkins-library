#!groovy
import groovy.json.JsonSlurperClassic

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

      //def cred_by_env_name = (env == 'prod') ? "prod-creds" : "nonprod-creds"
      def resp = steps.sh(script: "az keyvault secret show --vault-name 'infra-vault' --name '$secretName'", returnStdout: true).trim()
      secrets = new JsonSlurperClassic().parseText(resp)
      return new JsonSlurperClassic().parseText(secret.value)

      //Secrets loos like this: {"rg_name": "mgmt-state-store", "sa_name": "mgmtstatestore", "sa_container_name": "mgmtstatestorecontainer"}
    }
}
