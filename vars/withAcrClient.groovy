def withRegistrySecrets(Closure block) {
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


def call(String subscription, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.0.61-kubectl-1.13.5-helm-2.14.0', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        block.call()
      }
    }
  }
}


