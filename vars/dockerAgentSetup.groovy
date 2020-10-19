import uk.gov.hmcts.contino.azure.KeyVault

def call() {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    def envName = env.JENKINS_SUBSCRIPTION_NAME == "DTS-CFTSBOX-INTSVC" ? "sandbox" : "prod"
    echo "Using container env: ${envName}"
    // Check github host key
    int githubHostKeyCheck = sh(script: "grep '^github.com ssh-rsa' /home/jenkins/.ssh/known_hosts > /dev/null", returnStatus: "true")
    if (githubHostKeyCheck != 0) {
      sh """
        ssh-keyscan -t rsa github.com >> /home/jenkins/.ssh/known_hosts
        chmod 644 /home/jenkins/.ssh/known_hosts
      """
    }
    // Check github private key
    String infraVaultName = env.INFRA_VAULT_NAME
    String sshFile = "/home/jenkins/.ssh/id_rsa"
    String vaultSecret = "jenkins-ssh-private-key"
    String idRsa = "${envName}-${infraVaultName}-${vaultSecret}"
    if (env.CURRENT_ID_RSA != idRsa || !fileExists(sshFile)) {
      withSubscriptionLogin(envName) {
        KeyVault keyVault = new KeyVault(this, envName, infraVaultName)
        keyVault.download(vaultSecret, sshFile, "600")
        env.CURRENT_ID_RSA = idRsa
      }
    } else {
      echo "Using existing ssh id_rsa"
    }
  }
}
