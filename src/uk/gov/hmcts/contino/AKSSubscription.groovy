package uk.gov.hmcts.contino

class AKSSubscription implements Serializable {
  def final previewName

  def final aatName
  def final aatInfraRgName

  def final prodName
  def final prodInfraRgName

  def final perftestName
  def final perftestInfraRgName

  def final ithcName
  def final ithcInfraRgName

  AKSSubscription(Object env) {
    Objects.requireNonNull(env)
    previewName = env.AKS_PREVIEW_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    aatName = env.AKS_AAT_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    prodName = env.AKS_PROD_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-PROD'
    aatInfraRgName = env.AKS_AAT_INFRA_RESOURCE_GROUP ?: 'aks-infra-aat-rg'
    prodInfraRgName = env.AKS_PROD_INFRA_RESOURCE_GROUP ?: 'aks-infra-prod-rg'

    perftestName = env.AKS_PROD_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-PERFTEST'
    perftestInfraRgName = env.AKS_AAT_INFRA_RESOURCE_GROUP ?: 'aks-infra-perftest-rg'

    ithcName = env.AKS_ITHC_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-ITHC'
    ithcInfraRgName = env.AKS_ITHC_INFRA_RESOURCE_GROUP ?: 'aks-infra-ithc-rg'
  }

  static String aksEnvironment(String env) {
    String environment = env.toLowerCase()
    switch (environment) {
      case "sandbox": return "sbox"
      default: return environment
    }
  }
}
