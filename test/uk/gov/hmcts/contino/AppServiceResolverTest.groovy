package uk.gov.hmcts.contino

import spock.lang.Specification

class AppServiceResolverTest extends Specification {

  def steps
  def appServiceResolver

  void setup() {
    steps = Mock(JenkinsStepMock.class)

    appServiceResolver = new AppServiceResolver(steps)
  }

  def "getServiceHost should return error with invalid hostname"() {
    when:
      steps.sh(_) >> ''
      steps.env >> [SUBSCRIPTION_NAME: "sandbox"]
      steps.error(_) >> { throw new Exception(_ as String) }

      appServiceResolver.getServiceHost('custard', 'backend', 'sandbox')

    then:
      thrown(Exception)
  }

  def "getServiceHost with valid host name should return service host"() {
    def hostname = 'custard-backend-sandbox.service.core-compute-sandbox.internal'
    when:
      steps.sh(_) >> hostname
      steps.env >> [SUBSCRIPTION_NAME: "sandbox"]
      steps.error(_) >> { throw new Exception(_ as String) }

      def serviceUrl = appServiceResolver.getServiceHost('custard', 'backend', 'prod')

    then:
      serviceUrl == hostname
  }
}
