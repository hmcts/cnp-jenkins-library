#!groovy
import uk.gov.hmcts.contino.azure.KeyVault

def call(subscription, product, environment) {
  KeyVault keyVault = new KeyVault(this, subscription, "${product}-${environment}")
  def environmentVariables = []

  return environmentVariables
}
