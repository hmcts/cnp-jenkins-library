package uk.gov.hmcts.contino

class Environment implements Serializable {
  def final nonProdName
  def final prodName
  def final demoName
  def final previewName
  def final hmctsDemoName

  def final functionalTestEnvironments

  Environment(Object env) {
    Objects.requireNonNull(env)
    org.codehaus.groovy.runtime.NullObject.metaClass.toString = {return ''}

    nonProdName = env.NONPROD_ENVIRONMENT_NAME ?: 'aat'
    prodName = env.PROD_ENVIRONMENT_NAME ?: 'prod'
    demoName = env.DEMO_ENVIRONMENT_NAME ?: 'demo'
    previewName = env.PREVIEW_ENVIRONMENT_NAME ?: 'preview'
    hmctsDemoName = env.HMCTSDEMO_ENVIRONMENT_NAME ?: 'hmctsdemo'

    functionalTestEnvironments = [nonProdName, previewName]
  }

  def onFunctionalTestEnvironment(environment) {
    return functionalTestEnvironments.contains(environment)
  }
}
