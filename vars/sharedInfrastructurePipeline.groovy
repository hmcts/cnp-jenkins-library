import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

def call(String product, String environment, String subscription) {

  node {
    env.PATH = "$env.PATH:/usr/local/bin"

    def tfOutput

    stage('Checkout') {
      deleteDir()
      checkout scm
    }

    withSubscription(subscription) {
      withIlbIp(environment) {
        tfOutput = spinInfra(product, null, environment, false, subscription)
      }

      stage('Store shared product secrets') {
        if (!tfOutput.vaultName) {
          throw new IllegalStateException("No vault has been created to store the secrets in")
        }

        KeyVault keyVault = new KeyVault(this, subscription, tfOutput.vaultName.value)

        if (tfOutput.appInsightsInstrumentationKey) {
          keyVault.store(ProductVaultEntries.APP_INSIGHTS_INSTRUMENTATION_KEY, tfOutput.appInsightsInstrumentationKey.value)
        }
      }
    }

  }
}
