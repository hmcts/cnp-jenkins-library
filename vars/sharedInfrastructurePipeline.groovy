import uk.gov.hmcts.contino.azure.KeyVault

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
        tfOutput = spinInfra(product, environment, false, subscription)
      }

      stage('Store shared product secrets') {
        if (!tfOutput.vaultName) {
          throw new IllegalStateException("No vault has been created to store the secrets in")
        }

        KeyVault keyVault = new KeyVault(subscription, tfOutput.vaultName.value)

        if (tfOutput.appInsightsInstrumentationKey) {
          keyVault.store('AppInsightsInstrumentationKey', tfOutput.appInsightsInstrumentationKey.value)
        }
      }
    }

  }
}
