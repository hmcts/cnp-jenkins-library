def call(Closure block) {
  def registrySecrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-name', version: '', envVariable: 'REGISTRY_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-rg', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-sub', version: '', envVariable: 'REGISTRY_SUBSCRIPTION'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'docker-hub-username', version: '', envVariable: 'DOCKER_HUB_USERNAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'docker-hub-flux-token', version: '', envVariable: 'DOCKER_HUB_PASSWORD'],
  ]

  withAzureKeyvault(registrySecrets) {
    block.call()
  }
}
