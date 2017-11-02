package uk.gov.hmcts.contino

class MockDeployer implements Deployer, Serializable {
  @Override
  def deploy(String env) {
    return null
  }

  @Override
  def healthCheck(String env) {
    return null
  }

  @Override
  def getServiceUrl(String env) {
    return null
  }
}
