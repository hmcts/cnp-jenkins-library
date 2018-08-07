package uk.gov.hmcts.contino.azure

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

class AzTest extends Specification {

  static String SUBSCRIPTION = 'sandbox'

  def steps
  def testSubject

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    testSubject = new AzImpl(steps, SUBSCRIPTION)
  }

  def "Az should use cached credentials with the given subscription"() {
    when:
      testSubject.az 'mycommand'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${SUBSCRIPTION} az mycommand") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  class AzImpl extends Az {
    AzImpl(steps, subscription) {
      super(steps, subscription)
    }
  }
}
