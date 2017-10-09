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
    steps.unstash(product)
    deployer.deployJavaWebApp(env)
  }

  def healthCheck(String env) {
    deployer.healthCheck(env)
  }

  def getServiceUrl(String env) {
    return deployer.getServiceUrl(env)
  }
}
