import uk.gov.hmcts.contino.azure.KeyVault

def call(String product) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    def envName = env.JENKINS_SUBSCRIPTION_NAME == "DTS-CFTSBOX-INTSVC" ? "sandbox" : "prod"
    echo "Using container env: ${envName}"
    // setup github host key
    sh """
      ssh-keyscan -t rsa github.com >> /home/jenkins/.ssh/known_hosts
      chmod 700 /home/jenkins/.ssh
      chmod 644 /home/jenkins/.ssh/known_hosts
    """
    String infraVaultName = env.INFRA_VAULT_NAME
    String sshFile = "/home/jenkins/.ssh/id_rsa"
    String vaultSecret = "jenkins-ssh-private-key"
    String idRsa = "${envName}-${infraVaultName}-${vaultSecret}"
    if (env.CURRENT_ID_RSA != idRsa || !fileExists(sshFile)) {
      withSubscriptionLogin(envName) {
        KeyVault keyVault = new KeyVault(this, envName, infraVaultName)
        keyVault.download(vaultSecret, sshFile, "600")
        if (fileExists(sshFile)) {
          env.CURRENT_ID_RSA = idRsa
          echo "Downloaded ssh id_rsa"
        } else {
          env.CURRENT_ID_RSA = ""
          throw new Exception("Failed ssh id_rsa download")
        }
      }
    } else {
      echo "Using existing ssh id_rsa"
    }
  }
}
