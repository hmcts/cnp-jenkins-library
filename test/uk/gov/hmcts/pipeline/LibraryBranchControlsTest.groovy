package uk.gov.hmcts.pipeline

import spock.lang.Specification
import spock.lang.Unroll
import uk.gov.hmcts.contino.JenkinsStepMock

class LibraryBranchControlsTest extends Specification {
  static final String ALLOWLIST_URL = 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'

  def steps = Mock(JenkinsStepMock)
  def controls = new LibraryBranchControls(steps)

  def "sandbox Jenkins bypasses the allowlist check"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'sandbox', SHARED_LIBRARY_VERSION: 'test-branch']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    0 * steps.httpRequest(_)
  }

  @Unroll
  def "production subscription #subscription does not allow non-whitelisted branches"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: subscription, SHARED_LIBRARY_VERSION: 'test-branch', GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
    0 * steps.httpRequest(_)

    where:
    subscription << [null, 'prod']
  }

  @Unroll
  def "production subscription #subscription allows a whitelisted branch without querying GitHub tags"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: subscription, SHARED_LIBRARY_VERSION: 'allowed-branch', GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'allowed-branch', allowed: true]]]
    0 * steps.httpRequest(_)

    where:
    subscription << [null, 'prod']
  }

  @Unroll
  def "release reference #reference bypasses verification without HTTP requests"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: reference, GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    0 * steps.httpRequest(_)
    0 * steps.readYaml(_)

    where:
    // Includes an unverified version-shaped reference: this is the temporary bypass behaviour.
    reference << ['2.9.0', '2.11.2', '2.12.0', '99.0.0']
  }

  @Unroll
  def "non-release reference #reference still requires allowlisting"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: reference, GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
    0 * steps.httpRequest(_)

    where:
    reference << ['nonsemvertag', 'fix/test-branch', 'v2.11.2', '2.11.2-rc.1', '2.11', '2.11.2/README.md']
  }

  def "release reference does not need credentials before checkout"() {
    given:
    def env = [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '2.11.2']
    steps.env >> env

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    !env.containsKey('GIT_CREDENTIALS_ID')
    0 * steps.httpRequest(_)
    0 * steps.readYaml(_)
  }

  def "an explicitly disallowed branch remains blocked"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: 'blocked-branch', GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'blocked-branch', allowed: false]]]
    0 * steps.httpRequest(_)
  }
}
