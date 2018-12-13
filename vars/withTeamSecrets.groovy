import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, String environment, Closure block) {
  def mapToUse = new HashMap(pl.vaultSecrets)

  executeClosure(mapToUse, environment) {
    block.call()
  }
}

def executeClosure(secrets, String environment, Closure body) {
  def entry = secrets.entrySet().iterator().next()
  def keyVaultUrl = "https://${entry.key.replace('${env}', environment)}.vault.azure.net/"
  wrap([
    $class                   : 'AzureKeyVaultBuildWrapper',
    azureKeyVaultSecrets     : entry.value,
    keyVaultURLOverride      : keyVaultUrl,
    applicationIDOverride    : env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ]) {
    if (secrets.size() > 1) {
      secrets.remove(entry.key)
      return executeClosure(secrets, environment, body)
    } else {
      body.call()
    }
  }
}
