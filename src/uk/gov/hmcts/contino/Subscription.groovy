package uk.gov.hmcts.contino

class Subscription implements Serializable {
  def final nonProdName
  def final prodName
  def final demoName

  Subscription(Object env) {
    Objects.requireNonNull(env)

    nonProdName = env.NONPROD_SUBSCRIPTION_NAME ?: 'nonprod'
    prodName = env.PROD_SUBSCRIPTION_NAME ?: 'prod'
    demoName = env.DEMO_SUBSCRIPTION_NAME ?: 'nonprod'
  }
}
