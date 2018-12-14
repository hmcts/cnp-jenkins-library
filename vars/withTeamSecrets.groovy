import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, String environment, Closure block) {
  call(pl, environment, null, block)
}

def call(PipelineCallbacks pl, String environment, String keyVaultURL, Closure block) {
  Map<String, List<Map<String, Object>>> secrets = pl.vaultSecrets
  String vaultName = pl.vaultName

  if (secrets.size() == 0) {
    // no-op, supports empty secrets map
    body.call()
    return
  }

  def iterator = secrets.entrySet().iterator()
  executeClosure(iterator, environment, keyVaultURL, vaultName) {
    block.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String environment, String keyVaultURL, String vaultName, Closure body) {
  //noinspection ChangeToOperator doesn't work in jenkins
  def entry = secretIterator.next()

  String theKeyVaultUrl = getKeyVaultUrl(keyVaultURL, entry, environment, vaultName)

  wrap([
    $class                   : 'AzureKeyVaultBuildWrapper',
    azureKeyVaultSecrets     : entry.value,
    keyVaultURLOverride      : theKeyVaultUrl,
    applicationIDOverride    : env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ]) {
    if (secrets.hasNext()) {
      return executeClosure(secretIterator, environment, keyVaultURL, vaultName, body)
    } else {
      body.call()
    }
  }
}

@SuppressWarnings("GrMethodMayBeStatic") // no idea how a static method would work inside a jenkins step...
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
