import uk.gov.hmcts.contino.azure.KeyVault

def call(String product) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    def envName = env.JENKINS_SUBSCRIPTION_NAME == "DTS-CFTSBOX-INTSVC" ? "sandbox" : "prod"
    withSubscriptionLogin(envName) {
      def infraVaultName = env.INFRA_VAULT_NAME
      KeyVault keyVault = new KeyVault(this, envName, infraVaultName)
      keyVault.download("jenkins-ssh-private-key", "/home/jenkins/.ssh/id_rsa", "600")
    }
  }
}
