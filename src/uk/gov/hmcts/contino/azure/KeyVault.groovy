package uk.gov.hmcts.contino.azure;

/**
 * Needs to be used within a withSubscription block.
 */
class KeyVault implements Serializable {
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

  String find(String key) {
    try {
      return this.az("keyvault secret show --vault-name '${this.vaultName}' --name '${key}' --query value -o tsv".toString())
    } catch (e) {
      // Unfortunately Jenkins does not support returning both stdout and exit code yet, so we need to assume the
      // 'script returned exit code 1' was due to secret not being found or particular vault not being there.
      if (e.getMessage().contains("script returned exit code 1")) {
        return null
      } else {
        throw e
      }
    }
  }

}
