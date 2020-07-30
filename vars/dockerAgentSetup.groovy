import uk.gov.hmcts.pipeline.TeamConfig
import uk.gov.hmcts.contino.azure.KeyVault

def call(String product) {
  def teamConfig = new TeamConfig(this)
  if (teamConfig.isDockerBuildAgent(product)) {
    def envName = env.JENKINS_SUBSCRIPTION_NAME == "DTS-CFTSBOX-INTSVC" ? "sandbox" : "prod"
    withSubscriptionLogin(envName) {
      def infraVaultName = env.INFRA_VAULT_NAME
      KeyVault keyVault = new KeyVault(this, envName, infraVaultName)
      keyVault.download("jenkins-ssh-private-key", "/home/jenkins/.ssh/id_rsa", "600")
    }
  }
}
