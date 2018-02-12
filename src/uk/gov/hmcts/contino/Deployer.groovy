package uk.gov.hmcts.contino

interface Deployer {
  def deploy(String env)
  def healthCheck(String env, String slot)
  def getServiceUrl(String env, String slot)
}
