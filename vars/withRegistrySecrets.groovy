/**
 * Provides registry secrets from Azure Key Vault for ACR operations.
 * 
 * Supports dual ACR publish mode for transitioning between registries.
 * When DUAL_ACR_PUBLISH is enabled, images and charts are published to both
 * primary and secondary ACRs to enable smooth migration.
 * 
 * Environment variables provided:
 *   - REGISTRY_NAME: Primary ACR registry name
 *   - REGISTRY_RESOURCE_GROUP: Primary ACR resource group
 *   - REGISTRY_SUBSCRIPTION: Primary ACR subscription
 *   - DUAL_ACR_PUBLISH_ENABLED: 'true' when dual publish is active
 * 
 * Secondary ACR configuration (set in Jenkins environment or Jenkinsfile when dual mode enabled):
 *   - SECONDARY_REGISTRY_NAME: Secondary ACR registry name (e.g., 'hmctspublic')
 *   - SECONDARY_REGISTRY_RESOURCE_GROUP: Secondary ACR resource group
 *   - SECONDARY_REGISTRY_SUBSCRIPTION: Secondary ACR subscription
 */
def call(Closure block) {
  def registrySecrets = [
    // Primary ACR secrets (new ZA-enabled ACR)
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-name', version: '', envVariable: 'REGISTRY_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-rg', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-sub', version: '', envVariable: 'REGISTRY_SUBSCRIPTION'],
    // Docker Hub credentials
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'docker-hub-user', version: '', envVariable: 'DOCKER_HUB_USERNAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'docker-hub-jenkins-token', version: '', envVariable: 'DOCKER_HUB_PASSWORD'],
  ]

  // Check if dual ACR publish mode is enabled via environment variable
  def dualPublishEnabled = env.DUAL_ACR_PUBLISH?.toLowerCase() == 'true'
  
  withAzureKeyvault(registrySecrets) {
    if (dualPublishEnabled) {
      // Secondary ACR details are passed via environment variables (not KeyVault)
      // These should be set in Jenkins global config or the Jenkinsfile
      def secondaryName = env.SECONDARY_REGISTRY_NAME
      def secondaryRg = env.SECONDARY_REGISTRY_RESOURCE_GROUP
      def secondarySub = env.SECONDARY_REGISTRY_SUBSCRIPTION
      
      if (!secondaryName || !secondaryRg || !secondarySub) {
        error "Dual ACR publish is enabled but SECONDARY_REGISTRY_NAME, SECONDARY_REGISTRY_RESOURCE_GROUP, or SECONDARY_REGISTRY_SUBSCRIPTION is not set"
      }
      
      echo "Dual ACR publish mode is ENABLED - publishing to both primary (${env.REGISTRY_NAME}) and secondary (${secondaryName}) registries"
      withEnv(['DUAL_ACR_PUBLISH_ENABLED=true']) {
        block.call()
      }
    } else {
      withEnv(['DUAL_ACR_PUBLISH_ENABLED=false']) {
        block.call()
      }
    }
  }
}
