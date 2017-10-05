package uk.gov.hmcts.contino

class JavaDeployer implements Deployer, Serializable {

  WebAppDeploy deployer

  JavaDeployer(steps, product, app) {
    this.deployer = new WebAppDeploy(steps, product, app)
  }

  def deploy(String env) {
    deployer.deployJavaWebApp(env)
  }

  def healthCheck(String env) {
    deployer.healthCheck(env)
  }

  def getServiceUrl(String env) {
    return deployer.getServiceUrl(env)
  }
}
