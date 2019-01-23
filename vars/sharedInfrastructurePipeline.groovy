import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

def call(String product, String environment, String subscription) {
  call(product, environment, subscription, false)
}

def call(String product, String environment, String subscription, boolean planOnly) {

  node {
    env.PATH = "$env.PATH:/usr/local/bin"

    def tfOutput

    stage('Checkout') {
      deleteDir()
      checkout scm
    }

    withSubscription(subscription) {
      withIlbIp(environment) {
        tfOutput = spinInfra(product, null, environment, planOnly, subscription)
      }

      if (!planOnly) {
        stage('Store shared product secrets') {
          if (tfOutput.vaultName) {
            KeyVault keyVault = new KeyVault(this, subscription, tfOutput.vaultName.value)

            if (tfOutput.appInsightsInstrumentationKey) {
              keyVault.store(ProductVaultEntries.APP_INSIGHTS_INSTRUMENTATION_KEY, tfOutput.appInsightsInstrumentationKey.value)
            }
          } else {
            echo "No vault name, skipping storing vault secrets"
          }
        }
      }
    }
  }
}
