import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

@Deprecated
def call(String product, String environment, String subscription) {
  call(product, environment, subscription, false)
}

@Deprecated
def call(String product, String environment, String subscription, boolean planOnly) {
  call(product, environment, subscription, planOnly, null)
}

@Deprecated
def call(String product, String environment, String subscription, boolean planOnly, String deploymentTarget) {
  echo '''
================================================================================
sharedInfrastructurePipeline is
______                              _           _ _
|  _  \\                            | |         | | |
| | | |___ _ __  _ __ ___  ___ __ _| |_ ___  __| | |
| | | / _ \\ '_ \\| '__/ _ \\/ __/ _` | __/ _ \\/ _` | |
| |/ /  __/ |_) | | |  __/ (_| (_| | ||  __/ (_| |_|
|___/ \\___| .__/|_|  \\___|\\___\\__,_|\\__\\___|\\__,_(_)
          | |
          |_|
 Use withInfraPipeline instead
 https://github.com/hmcts/cnp-jenkins-library#opinionated-infrastructure-pipeline
================================================================================
'''

  node {
    try {
      env.PATH = "$env.PATH:/usr/local/bin"

      def tfOutput

      stageWithAgent('Checkout', product) {
        checkoutScm()
      }

      withSubscription(subscription) {
        tfOutput = spinInfra(product, null, environment, planOnly, subscription)
        if (deploymentTarget) {
          folderExists('deploymentTarget') {
            dir('deploymentTarget') {
              spinInfra(product, null, environment, planOnly, subscription, deploymentTarget)
            }
          }
        }

        if (!planOnly) {
          stageWithAgent('Store shared product secrets', product) {
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
    } finally {
      deleteDir()
    }
  }
}
