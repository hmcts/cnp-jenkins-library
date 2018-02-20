package uk.gov.hmcts.contino

public class StaticSiteDeployer implements Deployer, Serializable {
  WebAppDeploy deployer

  def dir

  StaticSiteDeployer(steps, product, app, dir) {
    this.deployer = new WebAppDeploy(steps, product, app)
    this.dir = dir
  }

  def deploy(String env) {
    deployer.deployStaticSite(env, dir)
  }

  def healthCheck(String env, String slot) {
    deployer.healthCheck(env, slot)
  }

  def getServiceUrl(String env, String slot) {
    return deployer.getServiceUrl(env, slot)
  }
}
