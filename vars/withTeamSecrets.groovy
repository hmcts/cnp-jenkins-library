import uk.gov.hmcts.contino.AppPipelineConfig

def call(AppPipelineConfig config, String environment, Closure body) {
  Map<String, List<Map<String, Object>>> secrets = config.vaultSecrets
  Map<String, String> vaultOverrides = config.vaultEnvironmentOverrides

  if (secrets.isEmpty()) {
    body.call()
    return
  }

  executeClosure(secrets.entrySet().iterator(), environment, vaultOverrides) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String environment, Map<String, String> vaultOverrides, Closure body) {
  //noinspection ChangeToOperator doesn't work in jenkins
  def entry = secretIterator.next()

  String theKeyVaultUrl = getKeyVaultUrl(entry, environment, vaultOverrides)

  withAzureKeyvault(
    azureKeyVaultSecrets: entry.value,
    keyVaultURLOverride: theKeyVaultUrl,
    applicationIDOverride: env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, environment, vaultOverrides, body)
    } else {
      body.call()
    }
  }
}

@SuppressWarnings("GrMethodMayBeStatic") // no idea how a static method would work inside a jenkins step...
private String getKeyVaultUrl(Map.Entry<String, List<Map<String, Object>>> entry, String environment, Map<String, String> vaultOverrides) {
  def vaultEnv = vaultOverrides.get(environment, environment)
  return "https://${entry.key.replace('${env}', vaultEnv)}.vault.azure.net/" // temp comment.TODO remove me
}
