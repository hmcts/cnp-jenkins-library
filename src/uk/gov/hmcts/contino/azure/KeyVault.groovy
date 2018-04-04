package uk.gov.hmcts.contino.azure;

/**
 * Needs to be run within a withSubscription block.
 */
class KeyVault {
  private String subscription
  private String vaultName

  private Closure az

  KeyVault(String vaultName) {
    this("jenkins", vaultName)
  }

  KeyVault(String subscription, String vaultName) {
    this.subscription = subscription
    this.vaultName = vaultName

    this.az = { cmd ->
      return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.subscription} az ${cmd}", returnStdout: true).trim()
    }
  }

  void store(String key, String value) {
    this.az("keyvault secret set --vault-name '${this.vaultName}' --name '${key}' --value '${value}'".toString())
  }

  String retrieve(String key) {
    return this.az("keyvault secret show --vault-name '${this.vaultName}' --name '${key}' --query value -o tsv".toString())
  }

}
