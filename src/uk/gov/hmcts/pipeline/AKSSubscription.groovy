package uk.gov.hmcts.pipeline

class AKSSubscription implements Serializable {
  def final name
  def final envName
  final boolean tlsEnabled
  def final steps
  def final keyvaultName

  AKSSubscription(Object steps, String name, String keyvaultName, String envName, boolean tlsEnabled) {
    this.keyvaultName = keyvaultName
    this.steps = steps
    this.name = name
    this.envName = envName
    this.tlsEnabled = tlsEnabled
  }

  String ingressIp() {
    def secrets = [
      [secretType: 'Secret', name: 'ingress-ip-staging', version: '', envVariable: 'INGRESS_IP'],
    ]
    steps.withAzureKeyvault(
      azureKeyVaultSecrets: secrets,
      keyVaultURLOverride: "https://${keyvaultName}.vault.azure.net"
    ) {
      return env.INGRESS_IP
    }
  }

  String loadBalancerIp() {
    def secrets = [
      [secretType: 'Secret', name: 'internal-lb-ip', version: '', envVariable: 'LB_IP'],
    ]
    steps.withAzureKeyvault(
      azureKeyVaultSecrets: secrets,
      keyVaultURLOverride: "https://${keyvaultName}.vault.azure.net"
    ) {
      return env.LB_IP
    }
  }
}
