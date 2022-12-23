package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class PactBrokerTest extends Specification {

  static final String PACT_BROKER_URL   = "https://pact-broker.platform.hmcts.net"
  static final String PACT_BROKER_IMAGE = "hmcts/pact-broker-cli"
  static final String PRODUCT           = 'product'
  static final String COMPONENT         = 'component'

  def steps
  def pactBroker
  def version

  void setup() {
    steps = Mock(JenkinsStepMock.class)
  }

  def "canIDeploy calls the pack-broker command in a container with the right parameters"() {
    given:
      pactBroker = new PactBroker(steps, PRODUCT, COMPONENT, PACT_BROKER_URL)
      version = "rand0mha5h"
      def closure
      steps.withDocker(_, _, { it.call() }) >> { closure = it }

    when:
      pactBroker.canIDeploy(version)

    then:
    1 * steps.sh({
      it.get('script').contains("pact-broker can-i-deploy --retry-while-unknown=12 --retry-interval=10 -a product_component -b ${PACT_BROKER_URL} -e ${version}") &&
        it.get('returnStdout').equals(true)
    })

  }

}
