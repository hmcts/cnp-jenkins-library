package uk.gov.hmcts.pipeline

import spock.lang.Specification
import spock.lang.Unroll
import uk.gov.hmcts.contino.JenkinsStepMock

class LibraryBranchControlsTest extends Specification {
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
    steps.env >> [PROD_SUBSCRIPTION_NAME: subscription, SHARED_LIBRARY_VERSION: 'test-branch']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed

    1 * steps.httpRequest({ request ->
      request.url == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'
    }) >> [content: 'allowlist']

    1 * steps.httpRequest({ request ->
      request.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '[]']

    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]

    where:
    subscription << [null, 'prod']
  }

  def "production subscription allows a whitelisted branch"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: subscription, SHARED_LIBRARY_VERSION: 'allowed-branch']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed

    1 * steps.httpRequest({ request ->
      request.url == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'
    }) >> [content: 'allowlist']

    1 * steps.httpRequest({ request ->
      request.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '[]']

    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'allowed-branch', allowed: true]]]

    where:
    subscription << [null, 'prod']
  }

  def "production subscription allows an existing and semantically versioned tag"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '2.9.0']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed

    1 * steps.httpRequest({ request ->
      request.url == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'
    }) >> [content: 'allowlist']

    1 * steps.httpRequest({ request ->
      request.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '[{"name": "2.9.0"},{"name":"2.1.0"}]']

    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
  }

  def "production subscription does not allow a tag that does not exist on GitHub"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: '22.0.0']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed

    1 * steps.httpRequest({ request ->
      request.url == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'
    }) >> [content: 'allowlist']

    1 * steps.httpRequest({ request ->
      request.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '[{"name": "22.1.0"},{"name":"22.0.1"}]']
    
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
  }

  def "production subscription does not allow an existing tag that does not follow semantic versioning"() {
    given:
    steps.env >> [PROD_SUBSCRIPTION_NAME: 'prod', SHARED_LIBRARY_VERSION: 'nonsemvertag']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    !allowed

    1 * steps.httpRequest({ request ->
      request.url == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/uk/gov/hmcts/library/allowed-library-branches.yml'
    }) >> [content: 'allowlist']

    1 * steps.httpRequest({ request ->
      request.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '[{"name": "nonsemvertag"}]']
    
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true]]]
  }

  def "malformed and unparsable library tag JSON is treated as no tags"() {
    given:
    steps.env >> [GIT_CREDENTIALS_ID: 'test-git-credentials']

    1 * steps.httpRequest({
      it.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '{not-valid-json']

    expect:
    controls.getLibraryTags() == []
  }

  def "library tag JSON with the wrong shape is treated as no tags"() {
    given:
    steps.env >> [GIT_CREDENTIALS_ID: 'test-git-credentials']

    1 * steps.httpRequest({
      it.url == 'https://api.github.com/repos/hmcts/cnp-jenkins-library/tags'
    }) >> [content: '{"name":"2.9.0"}']

    expect:
    controls.getLibraryTags() == []
  }
}
