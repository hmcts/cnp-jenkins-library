package uk.gov.hmcts.contino.azure

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

class AzTest extends Specification {

  static final String SUBSCRIPTION = 'sandbox'

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

  def "Az should use environment managed identity when running on matching environment agent"() {
    given:
      steps.env >> [
        DEPLOYMENT_ENVIRONMENT: 'preview',
        BUILD_AGENT_TYPE: 'ubuntu-preview'
      ]

    when:
      testSubject.az 'mycommand'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-preview az login --identity") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-preview az mycommand") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "Az should only login once per environment config"() {
    given:
      steps.env >> [
        DEPLOYMENT_ENVIRONMENT: 'preview',
        BUILD_AGENT_TYPE: 'ubuntu-preview'
      ]

    when:
      testSubject.az 'first'
      testSubject.az 'second'

    then:
      1 * steps.sh({it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-preview az login --identity")})
      1 * steps.sh({it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-preview az first")})
      1 * steps.sh({it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-preview az second")})
  }

  class AzImpl extends Az {
    AzImpl(steps, subscription) {
      super(steps, subscription)
    }
  }
}
