import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, String environment, Closure block) {
  call(pl, environment, null, block)
}

def call(PipelineCallbacks pl, String environment, String keyVaultURL, Closure block) {
  def mapToUse = new HashMap(pl.vaultSecrets)

  executeClosure(mapToUse, environment, keyVaultURL, pl.vaultName) {
    block.call()
  }
}

def executeClosure(Map<String, List<Map<String, Object>>> secrets, String environment, String keyVaultURL, String vaultName, Closure body) {
  if (secrets.size() == 0) {
    body.call()
    return
  }

  def entry = secrets.entrySet().iterator().next()

  String theKeyVaultUrl = getKeyVaultUrl(keyVaultURL, entry, environment, vaultName)

  wrap([
    $class                   : 'AzureKeyVaultBuildWrapper',
    azureKeyVaultSecrets     : entry.value,
    keyVaultURLOverride      : theKeyVaultUrl,
    applicationIDOverride    : env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ]) {
    if (secrets.size() > 1) {
      secrets.remove(entry.key)
      return executeClosure(secrets, environment, keyVaultURL, vaultName, body)
    } else {
      body.call()
    }
  }
}

private String getKeyVaultUrl(String keyVaultURL, Map.Entry<String, List<Map<String, Object>>> entry, String environment, String vaultName) {
  def theKeyVaultUrl
  if (keyVaultURL != null) {
    theKeyVaultUrl = keyVaultURL
  } else if (vaultName != null) {
    theKeyVaultUrl = "https://${(vaultName + '-' + environment)}.vault.azure.net/"
  } else {
    theKeyVaultUrl = "https://${entry.key.replace('${env}', environment)}.vault.azure.net/"
  }
  theKeyVaultUrl
}
