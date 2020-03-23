#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, Closure body) {
  try {
    echo "Attempting Managed Identity login to Azure (this will error if not supported)..."
    def azJenkins = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd") }
    azJenkins 'login --identity'
  } catch (ignored) {
    // no identity on the VM use SP instead
    echo "...Managed Identity login not supported, falling back to Service Principal login"
    echo "Error above can safely be ignored"
    servicePrincipalBasedLogin(subscription, body)
    return
  }
  identityBasedLogin(subscription, body)
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

def identityBasedLogin(String subscription, Closure body) {
  ansiColor('xterm') {
    def azJenkins = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
    azJenkins "account set --subscription ${env.JENKINS_SUBSCRIPTION_NAME}"
    def mgmtSubscriptionId = azJenkins 'account show --query id -o tsv'

    def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
    az 'login --identity'

    withAzureKeyvault([
      [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: "${subscription}-subscription-id", version: '', envVariable: 'ARM_SUBSCRIPTION_ID']
    ]) {
      az "account set --subscription ${env.ARM_SUBSCRIPTION_ID}"

      def infraVaultName = env.INFRA_VAULT_NAME
      log.info "Using $infraVaultName"

      log.warning "=== you are building with $subscription subscription credentials ==="

      def jenkinsObjectId = azJenkins "identity show -g managed-identities-${infraVaultName}-rg --name jenkins-${infraVaultName}-mi --query principalId -o tsv"

      def storageAccountKey = az "storage account keys  list --account-name mgmtstatestore${subscription} --query [0].value -o tsv"
      def tenantId = az "account show --query tenantId -o tsv"

      def tfStateRgNameTemplate = env.TF_STATE_RG_TEMPLATE ?: "mgmt-state-store"
      def tfStateStorageAccountNameTemplate = env.TF_STATE_RG_TEMPLATE ?: "mgmtstatestore"
      def tfStateContainerNameTemplate = env.TF_STATE_RG_TEMPLATE ?: "mgmtstatestorecontainer"
      def rootAddressSpace = env.ROOT_ADDRESS_SPACE ?: "10.96.0.0/12"


      withEnv([
        "ARM_USE_MSI=true",
        // Terraform env variables
        "ARM_ACCESS_KEY=${storageAccountKey}",
        "ARM_TENANT_ID=${tenantId}",
        // Terraform input variables
        "TF_VAR_tenant_id=${tenantId}",
        "TF_VAR_subscription_id=${env.ARM_SUBSCRIPTION_ID}",
        "TF_VAR_mgmt_subscription_id=${mgmtSubscriptionId}",
        "TF_VAR_token=${tenantId}",
        // other variables
        "STORE_rg_name_template=${tfStateRgNameTemplate}",
        "STORE_sa_name_template=${tfStateStorageAccountNameTemplate}",
        "STORE_sa_container_name_template=${tfStateContainerNameTemplate}",
        "SUBSCRIPTION_NAME=$subscription",
        "TF_VAR_jenkins_AAD_objectId=${jenkinsObjectId}",
        "TF_VAR_root_address_space=${rootAddressSpace}",
        "INFRA_VAULT_URL=https://${infraVaultName}.vault.azure.net/"
      ])
        {
          body.call()
        }
    }
  }
}
