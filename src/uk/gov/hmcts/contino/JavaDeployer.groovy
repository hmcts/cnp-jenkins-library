package uk.gov.hmcts.contino

class JavaDeployer implements Deployer, Serializable {

  def steps
  def product
  WebAppDeploy deployer

  JavaDeployer(steps, product, app) {
    this.steps = steps
    this.product = product
    this.deployer = new WebAppDeploy(steps, product, app)
  }

  def deploy(String env) {
//    steps.unstash(product)
    deployer.deployJavaWebApp(env)
  }

  def healthCheck(String env, String slot) {
    deployer.healthCheck(env, slot)
  }

  def getServiceUrl(String env, String slot) {
    return deployer.getServiceUrl(env, slot)
  }
}
