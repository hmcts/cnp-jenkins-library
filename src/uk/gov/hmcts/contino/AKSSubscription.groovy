package uk.gov.hmcts.contino

class AKSSubscription implements Serializable {
  def final aksPreviewName
  def final aksAatName

  AKSSubscription(Object env) {
    Objects.requireNonNull(env)
    aksPreviewName = env.AKS_PREVIEW_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    aksAatName = env.AKS_AAT_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
  }
}
