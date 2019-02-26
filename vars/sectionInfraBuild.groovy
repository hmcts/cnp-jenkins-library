import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

def call(params) {
  def pipelineConfig = params.pipelineConfig
  def environment = params.environment
  def subscription = params.subscription
  def product = params.product
  def planOnly = params.planOnly ?: false
  def deploymentTargets = params.deploymentTargets ?: deploymentTargets(subscription, environment)

  withSubscription(subscription) {
    withIlbIp(environment) {
      // build environment infrastructure once
      tfOutput = spinInfra(product, null, environment, planOnly, subscription)

      // build deployment target infrastructure for each deployment target
      folderExists('deploymentTarget') {
        dir('deploymentTarget') {
          for (int i = 0; i < deploymentTargets.size() ; i++) {
            spinInfra(product, null, environment, planOnly, subscription, deploymentTargets[i])
          }
        }
      }
    }

    // TODO get rid of this vault magic?
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
