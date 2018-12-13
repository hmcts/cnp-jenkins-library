import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, String environment, Closure block) {
  call(pl, environment, null, block)
}

def call(PipelineCallbacks pl, String environment, String keyvaultUrl, Closure block) {
  def mapToUse = new HashMap(pl.vaultSecrets)

  executeClosure(mapToUse, environment, keyvaultUrl) {
    block.call()
  }
}

def executeClosure(Map<String, List<Map<String, Object>>> secrets, String environment, String keyVaultUrl, Closure body) {
  if (secrets.size() == 0) {
    body.call()
    return
  }

  def entry = secrets.entrySet().iterator().next()

  String theKeyVaultUrl = determineKeyVaultUrl(keyVaultUrl, entry, environment)

  wrap([
    $class                   : 'AzureKeyVaultBuildWrapper',
    azureKeyVaultSecrets     : entry.value,
    keyVaultURLOverride      : theKeyVaultUrl,
    applicationIDOverride    : env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ]) {
    if (secrets.size() > 1) {
      secrets.remove(entry.key)
      return executeClosure(secrets, environment, keyVaultUrl, body)
    } else {
      body.call()
    }
  }
}

private String determineKeyVaultUrl(String keyVaultUrl, Map.Entry<String, List<Map<String, Object>>> entry, String environment) {
  def theKeyVaultUrl
  if (keyVaultUrl != null) {
    theKeyVaultUrl = keyVaultUrl
  } else {
    theKeyVaultUrl = "https://${entry.key.replace('${env}', environment)}.vault.azure.net/"
  }
  theKeyVaultUrl
}
