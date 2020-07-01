#!groovy
import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

def call(subscription, product, environment) {
  KeyVault keyVault = new KeyVault(this, subscription, "${product}-${environment}")
  def environmentVariables = []

  def appInsightsInstrumentationKey = keyVault.find(ProductVaultEntries.APP_INSIGHTS_INSTRUMENTATION_KEY)
  if (appInsightsInstrumentationKey) {
    environmentVariables.add("TF_VAR_appinsights_instrumentation_key=${appInsightsInstrumentationKey}")
  }

  return environmentVariables
}
