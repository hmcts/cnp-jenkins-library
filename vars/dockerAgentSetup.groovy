import uk.gov.hmcts.contino.azure.KeyVault

import java.nio.file.Files
import java.nio.file.Paths


def call(String agentContainer) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    def envName = env.JENKINS_SUBSCRIPTION_NAME == "DTS-CFTSBOX-INTSVC" ? "sandbox" : "prod"
    echo "Using container env: ${envName}"
    String infraVaultName = env.INFRA_VAULT_NAME
    String sshFile = "/home/jenkins/.ssh/id_rsa"
    String vaultSecret = "jenkins-ssh-private-key"
    String idRsa = "${envName}-${infraVaultName}-${vaultSecret}"
    echo "env.CURRENT_ID_RSA: ${env.CURRENT_ID_RSA}"
    echo "idRsa: ${idRsa}"
    echo "sshFile (${sshFile}): ${Files.exists(Paths.get(sshFile))}"
    if (env.CURRENT_ID_RSA != idRsa || !Files.exists(Paths.get(sshFile))) {
      withSubscriptionLogin(envName) {
        container(agentContainer) {
          echo "Using agent container: ${agentContainer}"
          KeyVault keyVault = new KeyVault(this, envName, infraVaultName)
          keyVault.download(vaultSecret, sshFile, "600")
          env.CURRENT_ID_RSA = idRsa
        }
      }
    } else {
      echo "Using existing ssh id_rsa"
    }
  }
}
