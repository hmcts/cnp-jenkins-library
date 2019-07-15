package uk.gov.hmcts.contino

class AKSSubscription implements Serializable {
  def final previewName
  def final aatName
  def final prodName
  def final aatInfraRgName
  def final prodInfraRgName

  AKSSubscription(Object env) {
    Objects.requireNonNull(env)
    previewName = env.AKS_PREVIEW_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    aatName = env.AKS_AAT_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    prodName = env.AKS_PROD_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-PROD'
    aatInfraRgName = env.AKS_AAT_INFRA_RESOURCE_GROUP ?: 'aks-infra-aat-rg'
    prodInfraRgName = env.AKS_PROD_INFRA_RESOURCE_GROUP ?: 'aks-infra-prod-rg'
  }

  public static String aksEnvironment(String env) {
    String environment = env.toLowerCase()
    switch (environment) {
      case "sandbox": "sbox"
      break
      default: environment
    }
  }
}
