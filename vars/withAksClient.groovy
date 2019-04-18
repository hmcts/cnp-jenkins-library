
def withRegistrySecrets(Closure block) {
  def registrySecrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-name', version: '', envVariable: 'REGISTRY_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-resource-group', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP'],
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


def call(String subscription, String environment, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.0.50-kubectl-1.12.2-helm-2.11', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        def envName = environment.toUpperCase()
        env.AKS_CLUSTER_NAME = env."${envName}_AKS_CLUSTER_NAME" ?: "cnp-${environment}-cluster"
        env.AKS_RESOURCE_GROUP = env."${envName}_AKS_RESOURCE_GROUP" ?: "cnp-${environment}-rg"
        block.call()
      }
    }
  }
}

def call(String subscription, Closure block) {
  String environment = new Environment(env).previewName
  call(subscription,environment, block)
}

