package uk.gov.hmcts.contino

class Environment implements Serializable {
  def final nonProdName
  def final prodName
  def final demoName
  def final previewName
  def final hmctsDemoName

  def final functionalTestEnvironments

  Environment(Object env, boolean includeSuffix = true) {
    Objects.requireNonNull(env)
    org.codehaus.groovy.runtime.NullObject.metaClass.toString = {return ''}

    nonProdName = envName(env.NONPROD_ENVIRONMENT_NAME, 'aat', env, includeSuffix)
    prodName = envName(env.PROD_ENVIRONMENT_NAME, 'prod', env, includeSuffix)
    demoName = envName(env.DEMO_ENVIRONMENT_NAME, 'demo', env, includeSuffix)
    previewName = envName(env.PREVIEW_ENVIRONMENT_NAME, 'preview', env, includeSuffix)
    hmctsDemoName = envName(env.HMCTSDEMO_ENVIRONMENT_NAME, 'hmctsdemo', env, includeSuffix)

    functionalTestEnvironments = [nonProdName, previewName]
  }

  def envName(envVar, defaultName, env, includeSuffix) {
    (envVar ?: defaultName) + (includeSuffix ? env.ENV_SUFFIX ?: "" : "")
  }

  def onFunctionalTestEnvironment(environment) {
    return functionalTestEnvironments.contains(environment)
  }
}
