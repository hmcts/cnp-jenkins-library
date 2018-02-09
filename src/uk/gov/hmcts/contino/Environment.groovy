package uk.gov.hmcts.contino

class Environment implements Serializable {
  def final nonProdName
  def final prodName

  Environment(Map<String, String> env) {
    Objects.requireNonNull(env)

    nonProdName = env.NONPROD_ENVIRONMENT_NAME ?: 'aat'
    prodName = env.PROD_ENVIRONMENT_NAME ?: 'prod'
  }
}
