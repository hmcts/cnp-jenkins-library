package uk.gov.hmcts.contino

class Subscription implements Serializable {
  def final nonProdName
  def final prodName
  def final demoName
  def final previewName
  def final hmctsDemoName
  def final qaName

  Subscription(Object env) {
    Objects.requireNonNull(env)

    nonProdName = env.NONPROD_SUBSCRIPTION_NAME ?: 'nonprod'
    prodName = env.PROD_SUBSCRIPTION_NAME ?: 'prod'
    demoName = env.DEMO_SUBSCRIPTION_NAME ?: 'nonprod'
    previewName = env.PREVIEW_SUBSCRIPTION_NAME ?: 'nonprod'
    hmctsDemoName = env.HMCTSDEMO_SUBSCRIPTION_NAME ?: 'hmctsdemo'
    qaName = env.QA_SUBSCRIPTION_NAME ?: 'qa'
  }
}
