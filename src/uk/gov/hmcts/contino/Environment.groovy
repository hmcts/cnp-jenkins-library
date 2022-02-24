package uk.gov.hmcts.contino

class Environment implements Serializable {
  def final nonProdName
  def final prodName
  def final demoName
  def final previewName
  def final perftestName
  def final ithcName
  def final sandbox
  def final pactBrokerUrl
  def final saat

  def final functionalTestEnvironments

  Environment(Object env) {
    Objects.requireNonNull(env)
    org.codehaus.groovy.runtime.NullObject.metaClass.toString = {return ''}

    nonProdName = env.NONPROD_ENVIRONMENT_NAME ?: 'aat'
    prodName = env.PROD_ENVIRONMENT_NAME ?: 'prod'
    demoName = env.DEMO_ENVIRONMENT_NAME ?: 'demo'
    previewName = env.PREVIEW_ENVIRONMENT_NAME ?: 'preview'
    perftestName = env.PERFTEST_ENVIRONMENT_NAME ?: 'perftest'
    ithcName = env.ITHC_ENVIRONMENT_NAME ?: 'ithc'
    sandbox = env.SANDBOX_ENVIRONMENT_NAME ?: 'sandbox'
    saat = env.SAAT_ENVIRONMENT_NAME ?: 'saat'
    pactBrokerUrl = env.PACT_BROKER_URL ?: 'https://pact-broker.platform.hmcts.net'

    functionalTestEnvironments = [nonProdName, previewName, 'idam-aat', 'idam-preview']
  }

  def onFunctionalTestEnvironment(environment) {
    return functionalTestEnvironments.contains(environment)
  }

  static String toTagName(String environment) {
    String cleanedEnvironment = environment.replace("idam-", "")
      .replace("packer-", "")
    switch(cleanedEnvironment) {
        case "sandbox":
        case "sbox":
        case "saat":
          return "sandbox"
        case "dev":
        case "preview":
          return "development"
        case "demo":
          return "demo"
        case "ithc":
          return "ithc"
        case "perftest":
        case "test":
          return "testing"
        case "stg":
        case "aat":
        case "aat2":
          return "staging"
        case "prod":
        case "prod2":
          return "production"
        default:
          throw new RuntimeException("Unknown environment: ${cleanedEnvironment}")
    }
  }

}
