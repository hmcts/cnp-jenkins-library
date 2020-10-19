package uk.gov.hmcts.contino.azure;

/**
 * Needs to be used within a withSubscriptionLogin block.
 */
class KeyVault extends Az implements Serializable {

  private String vaultName
  def steps

  KeyVault(steps, String vaultName) {
    this(steps, "jenkins", vaultName)
    this.steps = steps
  }

  KeyVault(steps, String subscription, String vaultName) {
    super(steps, subscription)
    this.vaultName = vaultName
    this.steps = steps
  }

  void store(String key, String value) {
    this.az("keyvault secret set --vault-name '${this.vaultName}' --name '${key}' --value '${value}'".toString())
  }

  String find(String key) {
    try {
      return this.az("keyvault secret show --vault-name '${this.vaultName}' --name '${key}' --query value -o tsv".toString())
    } catch (e) {
      // Unfortunately Jenkins does not support returning both stdout and exit code yet, so we need to assume the
      // 'script returned exit code n' was due to secret not being found or particular vault not being there.
      if (e.getMessage().contains("script returned exit code")) {
        return null
      } else {
        throw e
      }
    }
  }

  void download(String key, String path, String filePermissions) {
    String output
    // Reload file anyway, to be on the safe side
    try {
      this.steps.sh("rm -rf ${path}")
      output = this.az("keyvault secret download --vault-name '${this.vaultName}' --name '${key}' --file '${path}'")
      this.steps.sh("chmod ${filePermissions} '${path}'")
      this.steps.echo("File '${path}' with permissions '${filePermissions}' created from '${this.vaultName}' - '${key}' - output: ${output}")
    } catch (e) {
      // ignore as this must be a vm-agent
    }
  }

}
