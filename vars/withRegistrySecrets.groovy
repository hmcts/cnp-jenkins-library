def call(Closure block) {
  def registrySecrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-name', version: '', envVariable: 'REGISTRY_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-rg', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-sub', version: '', envVariable: 'REGISTRY_SUBSCRIPTION'],
  ]

  withAzureKeyvault(registrySecrets) {
    block.call()
  }
}
