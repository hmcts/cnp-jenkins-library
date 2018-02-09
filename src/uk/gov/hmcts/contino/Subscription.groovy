package uk.gov.hmcts.contino

class Subscription implements Serializable {
  def nonProdName = 'nonprod'
  def prodName = 'prod'

  Subscription(Map<String, String> env) {
    Objects.requireNonNull(env)

    if (env.NONPROD_SUBSCRIPTION_NAME) {
      nonProdName = env.NONPROD_SUBSCRIPTION_NAME
    }

    if (env.PROD_SUBSCRIPTION_NAME) {
      prodName = env.PROD_SUBSCRIPTION_NAME
    }
  }
}
