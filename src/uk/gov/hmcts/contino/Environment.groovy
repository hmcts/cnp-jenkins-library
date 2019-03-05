package uk.gov.hmcts.contino

class Environment implements Serializable {
  def final nonProdName
  def final prodName
  def final demoName
  def final previewName
  def final hmctsDemoName
  def final perftestName

  def final functionalTestEnvironments

  Environment(Object env) {
    Objects.requireNonNull(env)
    org.codehaus.groovy.runtime.NullObject.metaClass.toString = {return ''}

    nonProdName = (env.NONPROD_ENVIRONMENT_NAME ?: 'aat') + env.ENV_SUFFIX ?: ""
    prodName = (env.PROD_ENVIRONMENT_NAME ?: 'prod') + env.ENV_SUFFIX ?: ""
    demoName = (env.DEMO_ENVIRONMENT_NAME ?: 'demo') + env.ENV_SUFFIX ?: ""
    previewName = (env.PREVIEW_ENVIRONMENT_NAME ?: 'preview') + env.ENV_SUFFIX ?: ""
    hmctsDemoName = (env.HMCTSDEMO_ENVIRONMENT_NAME ?: 'hmctsdemo') + env.ENV_SUFFIX ?: ""
    perftestName = (env.PERFTEST_ENVIRONMENT_NAME ?: 'perftest') + env.ENV_SUFFIX ?: ""

    functionalTestEnvironments = [nonProdName, previewName]
  }

  def onFunctionalTestEnvironment(environment) {
    return functionalTestEnvironments.contains(environment)
  }
}
