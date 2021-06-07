
def call(String product, String environment, Map<String, String> vaultOverrides, Closure block) {
  def secrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'definition-importer-username', version: '', envVariable: 'DEFINITION_IMPORTER_USERNAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'definition-importer-password', version: '', envVariable: 'DEFINITION_IMPORTER_PASSWORD'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'ccd-api-gateway-oauth2-client-secret', version: '', envVariable: 'CCD_API_GATEWAY_OAUTH2_CLIENT_SECRET'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'ccd-api-gateway-s2s-key', version: '', envVariable: 'CCD_API_GATEWAY_S2S_KEY'],
  ]

  String theKeyVaultUrl = getKeyVaultUrl(product, environment, vaultOverrides)

  withAzureKeyvault(
    azureKeyVaultSecrets: secrets,
    keyVaultURLOverride: theKeyVaultUrl,
    applicationIDOverride: env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ) {
      block.call()
  }
}

@SuppressWarnings("GrMethodMayBeStatic")
private String getKeyVaultUrl(String product, String environment, Map<String, String> vaultOverrides) {
  def vaultEnv = vaultOverrides.get(environment, environment)
  return "https://${product}-${vaultEnv}.vault.azure.net/"
}
