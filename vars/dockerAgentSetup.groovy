import uk.gov.hmcts.contino.azure.KeyVault

def call(String product) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    def envName = env.JENKINS_SUBSCRIPTION_NAME == "DTS-CFTSBOX-INTSVC" ? "sandbox" : "prod"
    String infraVaultName = env.INFRA_VAULT_NAME
    String sshFile = "/home/jenkins/.ssh/id_rsa"
    String vaultSecret = "jenkins-ssh-private-key"
    String idRsa = "${envName}-${infraVaultName}-${vaultSecret}"
    if (env.CURRENT_ID_RSA != idRsa) {
      withSubscriptionLogin(envName) {
        KeyVault keyVault = new KeyVault(this, envName, infraVaultName)
        keyVault.download(vaultSecret, sshFile, "600")
        if (fileExists(sshFile)) {
          env.CURRENT_ID_RSA = idRsa
        } else {
          env.CURRENT_ID_RSA = ""
        }
      }
    }
  }
}
