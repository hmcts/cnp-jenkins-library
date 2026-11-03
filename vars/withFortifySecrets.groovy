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

  // The team vault credentials are only needed by a repo-level fortifyScan task (tenant-qualified
  // username + PAT). The library FoD runner authenticates with the Jenkins `fortify-on-demand-oauth`
  // credential, so a vault that simply does not hold these secrets must not fail the pipeline.
  // Anything else (abort/timeout, access denied, network) still fails as before, and once the block
  // has started its own errors propagate unchanged.
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

// The azure-keyvault plugin wraps every retrieval failure in AzureKeyVaultException("Failed to retrieve
// secret ... error message: ..."), so a missing secret is told apart from other failures by the Azure
// SDK's ResourceNotFoundException in the cause chain or the 404 / SecretNotFound details in the message.
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
