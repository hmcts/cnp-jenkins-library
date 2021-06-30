def call(String product, String environment, Closure block) {
  def fortifySecrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'fortify-on-demand-username', version: '', envVariable: 'FORTIFY_USER_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'fortify-on-demand-password', version: '', envVariable: 'FORTIFY_PASSWORD'],
  ]

  String theKeyVaultUrl = "https://${product}-${environment}.vault.azure.net/"

  withAzureKeyvault(
    azureKeyVaultSecrets: fortifySecrets,
    keyVaultURLOverride: theKeyVaultUrl
  ) {
    block.call()
  }
}
