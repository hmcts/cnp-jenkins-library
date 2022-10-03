import uk.gov.hmcts.contino.AppPipelineConfig

def call(AppPipelineConfig config, String environment, Closure body) {
  Map<String, List<Map<String, Object>>> secrets = config.vaultSecrets
  Map<String, String> vaultOverrides = config.vaultEnvironmentOverrides

  if (secrets.isEmpty()) {
    body.call()
    return
  }

  executeClosure(secrets.entrySet().iterator(), environment, config.highLevelDataSetupKeyVaultName, vaultOverrides) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String environment, String highLevelDataSetupKeyVaultName, Map<String, String> vaultOverrides, Closure body) {
  //noinspection ChangeToOperator doesn't work in jenkins
  def entry = secretIterator.next()

  String theKeyVaultUrl = getKeyVaultUrl(entry, environment, highLevelDataSetupKeyVaultName, vaultOverrides)

  withAzureKeyvault(
    azureKeyVaultSecrets: entry.value,
    keyVaultURLOverride: theKeyVaultUrl,
    applicationIDOverride: env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, environment, highLevelDataSetupKeyVaultName, vaultOverrides, body)
    } else {
      body.call()
    }
  }
}

@SuppressWarnings("GrMethodMayBeStatic") // no idea how a static method would work inside a jenkins step...
private String getKeyVaultUrl(Map.Entry<String, List<Map<String, Object>>> entry, String environment, String highLevelDataSetupKeyVaultName, Map<String, String> vaultOverrides) {
  def vaultEnv = vaultOverrides.get(environment, environment)

    String theKeyVaultUrl = ""
    if (!highLevelDataSetupKeyVaultName?.trim()) {
        theKeyVaultUrl = "https://${entry.key.replace('${env}', vaultEnv)}.vault.azure.net/"
    } else {
        if (entry.key.equalsIgnoreCase(highLevelDataSetupKeyVaultName)) {
            theKeyVaultUrl = "https://${entry.key.replace(highLevelDataSetupKeyVaultName, vaultEnv)}.vault.azure.net/"
        } else {
            theKeyVaultUrl = "https://${entry.key.replace('${env}', vaultEnv)}.vault.azure.net/"

        }
    }
  return theKeyVaultUrl
}
