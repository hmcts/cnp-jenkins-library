package uk.gov.hmcts.pipeline

import spock.lang.Specification
import spock.lang.Unroll
import uk.gov.hmcts.contino.JenkinsStepMock

class LibraryBranchControlsTest extends Specification {
  def steps = Mock(JenkinsStepMock)
  def controls = new LibraryBranchControls(steps)

  def "sandbox Jenkins bypasses the allowlist"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'sandbox', SHARED_LIBRARY_VERSION: 'test-branch']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    0 * steps.httpRequest(_)
  }

  @Unroll
  def "production subscription #subscription keeps allowlist enforcement enabled"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: subscription, SHARED_LIBRARY_VERSION: 'test-branch']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed
    1 * steps.httpRequest(_) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]

    where:
    subscription << [null, 'prod']
  }
}
