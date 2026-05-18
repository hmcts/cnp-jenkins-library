#!groovy
import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.pipeline.AgentSelector

def call(String subscription, Closure body) {
  call(subscription, env.PRODUCT ?: env.RAW_PRODUCT_NAME ?: '', body)
}

def call(String subscription, String product, Closure body) {
  identityBasedLogin(subscription, product, env.DEPLOYMENT_ENVIRONMENT ?: null, body)
}

def call(String subscription, String product, String environment, Closure body) {
  identityBasedLogin(subscription, product, environment, body)
}

def identityBasedLogin(String subscription, String product, String environment, Closure body) {
  ansiColor('xterm') {
    def mgmtSubscriptionId
    def jenkinsObjectId

    withTargetSubscriptionIdentity(subscription, product, environment) {
      withSubscriptionLogin(subscription) {
        def infraVaultName = env.INFRA_VAULT_NAME
        log.info "Using $infraVaultName"

        log.warning "=== you are building with $subscription subscription credentials ==="

        boolean usePtlJenkinsIdentity = usePtlJenkinsIdentity(product, environment)
        String normalisedEnvironment = AgentSelector.normaliseEnvironment(environment)
        String managedIdentityResourceGroupEnvironment = AgentSelector.managedIdentityResourceGroupEnvironment(environment)
        String jenkinsAzureConfigDir = product ? "/opt/jenkins/.azure-${usePtlJenkinsIdentity ? 'ptl' : subscription}" : '/opt/jenkins/.azure-jenkins'
        withJenkinsIdentity(product, environment) {
          Closure azJenkins = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=${jenkinsAzureConfigDir} az $cmd", returnStdout: true).trim() }
          azJenkins 'login --identity'
          if (!product || usePtlJenkinsIdentity) {
            azJenkins "account set --subscription ${env.JENKINS_SUBSCRIPTION_NAME}"
          }
          mgmtSubscriptionId = usePtlJenkinsIdentity || !product ?
            azJenkins('account show --query id -o tsv') :
            env.JENKINS_SUBSCRIPTION_ID ?: azJenkins('account show --query id -o tsv')

          String identityResourceGroupName = usePtlJenkinsIdentity || !product ?
            "managed-identities-${infraVaultName}-rg" :
            "managed-identities-${managedIdentityResourceGroupEnvironment}-rg"
          String identityName = usePtlJenkinsIdentity || !product ?
            "jenkins-${infraVaultName}-mi" :
            "jenkins-${normalisedEnvironment}-mi"
          jenkinsObjectId = azJenkins("identity show -g ${identityResourceGroupName} --name ${identityName} --query principalId -o tsv")
        }

        def tfStateRgNameTemplate = env.TF_STATE_RG_TEMPLATE ?: "mgmt-state-store"
        def tfStateStorageAccountNameTemplate = env.TF_STATE_STORAGE_TEMPLATE ?: "mgmtstatestore"
        def tfStateContainerNameTemplate = env.TF_STATE_CONTAINER_TEMPLATE ?: "mgmtstatestorecontainer"
        def rootAddressSpace = env.ROOT_ADDRESS_SPACE ?: "10.96.0.0/12"

        Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
        def tenantId = az "account show --query tenantId -o tsv"
        def storageAccountKey = az "storage account keys list --account-name ${tfStateStorageAccountNameTemplate}${subscription} --resource-group ${tfStateRgNameTemplate}-${subscription} --query [0].value -o tsv"

        withEnv([
          "ARM_USE_MSI=true",
          // Terraform env variables
          "ARM_ACCESS_KEY=${storageAccountKey}",
          // Terraform input variables
          "TF_VAR_tenant_id=${tenantId}",
          "TF_VAR_subscription_id=${env.ARM_SUBSCRIPTION_ID}",
          "TF_VAR_mgmt_subscription_id=${mgmtSubscriptionId}",
          "TF_VAR_token=${tenantId}",
          // other variables
          "STORE_rg_name_template=${tfStateRgNameTemplate}",
          "STORE_sa_name_template=${tfStateStorageAccountNameTemplate}",
          "STORE_sa_container_name_template=${tfStateContainerNameTemplate}",
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
}

def withTargetSubscriptionIdentity(String subscription, String product, String environment, Closure body) {
  if (product && environment) {
    String targetEnvironment = targetIdentityEnvironment(subscription, environment)
    if (AgentSelector.isEnvironmentLikeSubscription(subscription)) {
      String agentLabel = AgentSelector.labelForEnvironment(targetEnvironment, env, product) ?: "ubuntu-${targetEnvironment}"
      withEnvironmentAgent(targetEnvironment, product, agentLabel, body)
    } else {
      withEnvironmentAgent(targetEnvironment, product, body)
    }
  } else {
    body.call()
  }
}

def withJenkinsIdentity(String product, String environment, Closure body) {
  if (usePtlJenkinsIdentity(product, environment)) {
    withEnvironmentAgent('ptl', product, 'ubuntu-ptl', body)
  } else {
    body.call()
  }
}

boolean usePtlJenkinsIdentity(String product, String environment) {
  if (!product) {
    return false
  }
  return AgentSelector.normaliseEnvironment(environment) == 'ptl'
}

String targetIdentityEnvironment(String subscription, String environment) {
  // Env-like SDS subscriptions own their Terraform state stores, so run Azure
  // login on that subscription's MI. CFT-style aliases such as nonprod/qa keep
  // routing by the concrete environment, e.g. aat or preview.
  if (AgentSelector.isEnvironmentLikeSubscription(subscription)) {
    return AgentSelector.normaliseEnvironment(subscription)
  }
  return environment
}
