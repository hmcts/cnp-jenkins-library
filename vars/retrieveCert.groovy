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
      def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

      az 'login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      az 'account set --subscription $JENKINS_SUBSCRIPTION_ID'

      log.info "using ${env.INFRA_VAULT_NAME}"

      // check certificate exists
      az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name core-compute-${environment}"

      // Setting environment vars consumed by TF
      env.TF_VAR_certificateName = "core-compute-${environment}"
      thumbPrint = az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name ${env.TF_VAR_certificateName} --query x509ThumbprintHex --output tsv"
      if (thumbPrint.contains("not found"))
      {
        defaultPolicy = libraryResource 'uk/gov/hmcts/contino/certificateDefaultPolicy.json'
        log.info("Certificate name ${env.TF_VAR_certificateName} does not exist in vault ${env.INFRA_VAULT_NAME}! Creating one right now...")
        az(/keyvault certificate create --vault-name ${env.INFRA_VAULT_NAME} --name ${env.TF_VAR_certificateName} --policy "${defaultPolicy}"/)
        log.info("Retrieving the thumbprint")
        env.TF_VAR_certificateThumbprint = az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name ${env.TF_VAR_certificateName} --query x509ThumbprintHex --output tsv"
      }
      else
        env.TF_VAR_certificateThumbprint = thumbPrint

      echo "Setting subscription back to $env.ARM_SUBSCRIPTION_ID for Azure CLI"
      az 'login --service-principal -u $ARM_CLIENT_ID -p $ARM_CLIENT_SECRET -t $ARM_TENANT_ID'
      az 'account set --subscription $ARM_SUBSCRIPTION_ID'
    }
}
