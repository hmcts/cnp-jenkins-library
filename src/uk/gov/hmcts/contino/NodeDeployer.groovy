package uk.gov.hmcts.contino;

public class NodeDeployer implements Deployer, Serializable {
  WebAppDeploy deployer

  NodeDeployer(steps, product, app) {
    this.deployer = new WebAppDeploy(steps, product, app)
  }

  def deploy(String env) {
    deployer.deployNodeJS(env)
  }

  def healthCheck(String env, String slot) {
    deployer.healthCheck(env, slot)
  }

  def getServiceUrl(String env, String slot) {
    return deployer.getServiceUrl(env, slot)
  }
}
