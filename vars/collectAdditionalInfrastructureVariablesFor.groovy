#!groovy
import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

def call(subscription, product, environment) {
  KeyVault keyVault = new KeyVault(this, subscription, "${product}-${environment}")
  def environmentVariables = []

  return environmentVariables
}
