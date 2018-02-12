package uk.gov.hmcts.contino

class MockDeployer implements Deployer, Serializable {
  @Override
  def deploy(String env) {
    return null
  }

  @Override
  def healthCheck(String env, String slot) {
    return null
  }

  @Override
  def getServiceUrl(String env, String slot) {
    return null
  }
}
