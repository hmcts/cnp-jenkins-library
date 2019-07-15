package uk.gov.hmcts.contino

class AKSSubscription implements Serializable {
  def final previewName
  def final aatName
  def final prodName

  AKSSubscription(Object env) {
    Objects.requireNonNull(env)
    previewName = env.AKS_PREVIEW_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    aatName = env.AKS_AAT_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    prodName = env.AKS_PROD_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-PROD'
  }
}
