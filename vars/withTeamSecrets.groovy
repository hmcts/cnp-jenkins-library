import uk.gov.hmcts.contino.AppPipelineConfig

def call(AppPipelineConfig config, String environment, Closure block) {
  call(config, environment, null, block)
}

def call(AppPipelineConfig config, String environment, String keyVaultURL, Closure body) {
  Map<String, List<Map<String, Object>>> secrets = config.vaultSecrets
  Map<String, String> vaultOverrides = config.vaultEnvironmentOverrides
  String vaultName = config.vaultName

  if (secrets.isEmpty()) {
    body.call()
    return
  }

  executeClosure(secrets.entrySet().iterator(), environment, keyVaultURL, vaultName, vaultOverrides) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String environment, String keyVaultURL, String vaultName, Map<String, String> vaultOverrides, Closure body) {
  //noinspection ChangeToOperator doesn't work in jenkins
  def entry = secretIterator.next()

  String theKeyVaultUrl = getKeyVaultUrl(keyVaultURL, entry, environment, vaultName, vaultOverrides)

  withAzureKeyvault(azureKeyVaultSecrets: entry.value, keyVaultURLOverride: theKeyVaultUrl) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, environment, keyVaultURL, vaultName, vaultOverrides, body)
    } else {
      body.call()
    }
  }
}

@SuppressWarnings("GrMethodMayBeStatic") // no idea how a static method would work inside a jenkins step...
private String getKeyVaultUrl(String keyVaultURL, Map.Entry<String, List<Map<String, Object>>> entry, String environment, String vaultName, Map<String, String> vaultOverrides) {
  def theKeyVaultUrl
  def vaultEnv = vaultOverrides.get(environment, environment)
  if (keyVaultURL != null) {
    theKeyVaultUrl = keyVaultURL
  } else if (vaultName != null) {
    theKeyVaultUrl = "https://${(vaultName + '-' + vaultEnv)}.vault.azure.net/"
  } else {
    theKeyVaultUrl = "https://${entry.key.replace('${env}', vaultEnv)}.vault.azure.net/"
  }
  theKeyVaultUrl
}
