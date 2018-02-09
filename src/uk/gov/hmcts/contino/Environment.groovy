package uk.gov.hmcts.contino

class Environment implements Serializable {
  def nonProdName = 'aat'
  def prodName = 'prod'

  Environment(Map<String, String> env) {
    Objects.requireNonNull(env)

    if (env.NONPROD_ENVIRONMENT_NAME) {
      nonProdName = env.NONPROD_ENVIRONMENT_NAME
    }

    if (env.PROD_ENVIRONMENT_NAME) {
      prodName = env.PROD_ENVIRONMENT_NAME
    }
  }

}
