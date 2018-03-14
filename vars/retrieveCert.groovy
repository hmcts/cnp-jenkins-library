#!groovy
import groovy.json.JsonSlurperClassic

def call(String environment) {

  echo "Retrieving certificate for ${environment} from vault"

  withCredentials([azureServicePrincipal(
    credentialsId: "jenkinsServicePrincipal",
    subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
    clientIdVariable: 'JENKINS_CLIENT_ID',
    clientSecretVariable: 'JENKINS_CLIENT_SECRET',
    tenantIdVariable: 'JENKINS_TENANT_ID')])
    {
      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'
      if("${environment}" == "sandbox"){
        env.TF_VAR_vaultName = "infra-vault-sandbox"
      }
      else{
        env.TF_VAR_vaultName = "infra-vault"
      }
      // Setting environment vars consumed by TF
      sh "az keyvault certificate show --vault-name $TF_VAR_vaultName --name core-compute-${environment} --query x509ThumbprintHex --output tsv > thumbhex.txt"
      env.TF_VAR_certificateThumbprint = readFile('thumbhex.txt')
      env.TF_VAR_certificateName = "core-compute-${environment}"
      // if(["sandbox","saat","sprod"]).contains("${environment}"){
      //   env.TF_VAR_vaultName = "infra-vault-sandbox"
      // }
      echo "Setting subscription back to $env.ARM_SUBSCRIPTION_ID for Azure CLI"
      sh 'az login --service-principal -u $ARM_CLIENT_ID -p $ARM_CLIENT_SECRET -t $ARM_TENANT_ID'
      sh 'az account set --subscription $ARM_SUBSCRIPTION_ID'

    }




}
