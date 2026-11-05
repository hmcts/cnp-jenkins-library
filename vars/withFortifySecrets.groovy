import com.cloudbees.groovy.cps.NonCPS

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

  // Missing vault secrets are tolerated (the library runner uses Jenkins OAuth); other errors still fail.
  boolean secretsLoaded = false
  try {
    withAzureKeyvault(azureKeyVaultSecrets: fortifySecrets, keyVaultURLOverride: theKeyVaultUrl) {
      secretsLoaded = true
      block.call()
    }
  } catch (Exception e) {
    if (secretsLoaded || e instanceof InterruptedException || !isSecretNotFound(e)) {
      throw e
    }
    echo("WARNING: Fortify: fortify-on-demand-username/password not found in Key Vault ${fortifyVaultName}; " +
      "proceeding without Fortify scan credentials. The library FoD scan runner uses the Jenkins OAuth credential " +
      "instead; a repo-level fortifyScan task will fail to authenticate without these secrets.")
    block.call()
  }
}

// Detects a missing secret from the cause chain or the plugin's wrapped 404 / SecretNotFound message.
@NonCPS
private boolean isSecretNotFound(Throwable e) {
  for (Throwable t = e; t != null; t = (t.cause == t ? null : t.cause)) {
    if (t.class.simpleName == 'ResourceNotFoundException') {
      return true
    }
    String message = t.message ?: ''
    if (message.contains('SecretNotFound') || message.contains('Status code 404') || message =~ /Secret: \S+ not found in vault/) {
      return true
    }
  }
  return false
}
