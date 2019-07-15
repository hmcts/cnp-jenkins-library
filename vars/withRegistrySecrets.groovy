def call(Closure block) {
  def registrySecrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-name', version: '', envVariable: 'REGISTRY_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-rg', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'public-registry-sub', version: '', envVariable: 'REGISTRY_SUBSCRIPTION'],
  ]

  wrap([$class                   : 'AzureKeyVaultBuildWrapper',
        azureKeyVaultSecrets     : registrySecrets,
        keyVaultURLOverride      : env.INFRA_VAULT_URL,
        applicationIDOverride    : env.AZURE_CLIENT_ID,
        applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ]) {
    block.call()
  }
}
