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

  onHMCTSDemo {
    keyVault = new KeyVault(this, subscription, "infra-vault-hmctsdemo")

    def hmctsdemoTenantId = keyVault.find("security-aad-tenantId")
    environmentVariables.add("TF_VAR_security_aad_tenantId=${hmctsdemoTenantId}")

    def hmctsdemoClientId = keyVault.find("security-aad-clientId")
    environmentVariables.add("TF_VAR_security_aad_clientId=${hmctsdemoClientId}")

    def hmctsdemoClientSecret = keyVault.find("security-aad-clientSecret")
    environmentVariables.add("TF_VAR_security_aad_clientSecret=${hmctsdemoClientSecret}")
  }
  return environmentVariables
}
