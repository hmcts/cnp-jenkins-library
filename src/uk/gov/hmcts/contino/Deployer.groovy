package uk.gov.hmcts.contino

interface Deployer {
  def deploy(String env)
  def healthCheck(String env)
  def getServiceUrl(String env)
}
