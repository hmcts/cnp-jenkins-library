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

      def functions = libraryResource 'uk/gov/hmcts/contino/getCert.sh'
      writeFile file: 'getCert.sh', text: functions
      result = sh "bash getCert.sh ${environment}"

      // Setting environment vars consumed by TF
      sh "az keyvault certificate show --vault-name infra-vault-${environment} --name ${environment} --query x509ThumbprintHex --output tsv > thumbhex.txt"
      def String secret = sh(script: "az keyvault secret show --vault-name infra-vault-$environment --name ${environment}CertPW --query value", returnStdout: true).trim()
      def String pfxPass = secret.substring(1, secret.length()-1)
      env.TF_VAR_pfxFile = "${WORKSPACE}/${environment}.pfx"
      env.TF_VAR_certificateThumbprint = readFile('thumbhex.txt')
      env.TF_VAR_certificateName = "${environment}"
      env.TF_VAR_pfxBlobString = readFile('base64.txt')
      env.TF_VAR_password = "${pfxPass}"
      echo "Setting subscription back to $env.ARM_SUBSCRIPTION_ID for Azure CLI"
      sh 'az login --service-principal -u $ARM_CLIENT_ID -p $ARM_CLIENT_SECRET -t $ARM_TENANT_ID'
      sh 'az account set --subscription $ARM_SUBSCRIPTION_ID'

    }




}
