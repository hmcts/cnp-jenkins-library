def call(String fortifyVaultName = null, Closure block) {
  if (!fortifyVaultName?.trim()) {
    echo('Fortify: missing fortifyVaultName; proceeding without Fortify scan credentials')
    block.call()
    return
  }

  def fortifySecrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'fortify-on-demand-username', version: '', envVariable: 'FORTIFY_USER_NAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'fortify-on-demand-password', version: '', envVariable: 'FORTIFY_PASSWORD'],
  ]

  String theKeyVaultUrl = "https://${fortifyVaultName}.vault.azure.net"
  withAzureKeyvault(azureKeyVaultSecrets: fortifySecrets, keyVaultURLOverride: theKeyVaultUrl) {
    block.call()
  }
}
