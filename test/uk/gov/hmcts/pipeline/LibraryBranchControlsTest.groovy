package uk.gov.hmcts.pipeline

import jenkins.scm.api.SCMSource
import spock.lang.Specification
import spock.util.mop.ConfineMetaClassChanges
import spock.lang.Unroll
import uk.gov.hmcts.contino.JenkinsStepMock

class LibraryBranchControlsTest extends Specification {
  static final String ALLOWLIST_URL = 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'
  static final String TAG_REF_URL = 'https://api.github.com/repos/hmcts/cnp-jenkins-library/git/ref/tags/'

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
    0 * steps.httpRequest({ it.url.startsWith(TAG_REF_URL) })

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
    0 * steps.httpRequest({ it.url.startsWith(TAG_REF_URL) })

    where:
    subscription << [null, 'prod']
  }

  def "allowlisted release tag does not query GitHub tags"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '2.9.0', GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: '2.9.0', allowed: true]]]
    0 * steps.httpRequest({ it.url.startsWith(TAG_REF_URL) })
  }

  @Unroll
  def "non-allowlisted release tag is allowed only when GitHub returns #status"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '2.11.1', GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed == (status == 200)
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
    1 * steps.httpRequest({ it.url == TAG_REF_URL + '2.11.1' && it.authentication == 'creds' }) >> [status: status]

    where:
    status << [200, 404]
  }

  def "a non-semver reference is not looked up as a tag"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: 'nonsemvertag', GIT_CREDENTIALS_ID: 'creds']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
    0 * steps.httpRequest({ it.url.startsWith(TAG_REF_URL) })
  }

  def "release tag is not looked up anonymously when no credential can be resolved"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '2.11.1']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
    0 * steps.httpRequest({ it.url.startsWith(TAG_REF_URL) })
  }

  @ConfineMetaClassChanges(SCMSource.SourceByItem)
  def "release tag lookup resolves the SCM credential before checkout has set GIT_CREDENTIALS_ID"() {
    given:
    def env = [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '2.11.1']
    def job = new Object()
    // Stands in for the job's GitHubSCMSource; only credentialsId is read.
    def scmSource = [credentialsId: 'scm-creds']
    SCMSource.SourceByItem.metaClass.static.findSource = { Object item -> item.is(job) ? scmSource : null }
    steps.env >> env
    steps.currentBuild >> [rawBuild: [parent: job]]

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    env.GIT_CREDENTIALS_ID == 'scm-creds'
    1 * steps.httpRequest({ it.url == ALLOWLIST_URL }) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
    1 * steps.httpRequest({ it.url == TAG_REF_URL + '2.11.1' && it.authentication == 'scm-creds' }) >> [status: 200]
  }
}
