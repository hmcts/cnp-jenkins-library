package uk.gov.hmcts.contino.azure;

/**
 * Needs to be used within a withSubscription block.
 */
class KeyVault {
  private steps
  private String subscription
  private String vaultName

  private Closure az

  KeyVault(steps, String vaultName) {
    this(steps, "jenkins", vaultName)
  }

  KeyVault(steps, String subscription, String vaultName) {
    this.steps = steps
    this.subscription = subscription
    this.vaultName = vaultName

    this.az = { cmd ->
      return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.subscription} az ${cmd}", returnStdout: true).trim()
    }
  }

  void store(String key, String value) {
    this.az("keyvault secret set --vault-name '${this.vaultName}' --name '${key}' --value '${value}'".toString())
  }

  Optional<String> find(String key) {
    try {
      return Optional.of(this.az("keyvault secret show --vault-name '${this.vaultName}' --name '${key}' --query value -o tsv".toString()))
    } catch (Exception e) {
      println e.getClass().toString()
      if (e.getMessage().contains("Secret not found")) {
        return Optional.empty()
      } else {
        throw e
      }
    }
  }

}
