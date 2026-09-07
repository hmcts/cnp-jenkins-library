package uk.gov.hmcts.pipeline

import spock.lang.Specification
import spock.lang.Unroll
import uk.gov.hmcts.contino.JenkinsStepMock

class LibraryBranchControlsTest extends Specification {
  def steps = Mock(JenkinsStepMock)
  def controls = new LibraryBranchControls(steps)

  @Unroll
  def "skip flag #skipCheck allows test branches without fetching the allowlist"() {
    given:
    steps.env >> [SKIP_LIBRARY_BRANCH_CHECK: skipCheck, SHARED_LIBRARY_VERSION: 'test-branch']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed
    0 * steps.httpRequest(_)
    0 * steps.readYaml(_)
    1 * steps.echo('Skipping library branch allowlist validation because SKIP_LIBRARY_BRANCH_CHECK=true.')

    where:
    skipCheck << ['true', 'TRUE', ' True ']
  }

  @Unroll
  def "skip flag #skipCheck enforces allowlist for #branch"() {
    given:
    steps.env >> [SKIP_LIBRARY_BRANCH_CHECK: skipCheck, JENKINS_SUBSCRIPTION_NAME: 'DTS-CFTSBOX-INTSVC', SHARED_LIBRARY_VERSION: branch,
                  SUBSCRIPTION_NAME: 'sandbox', NONPROD_SUBSCRIPTION_NAME: 'sandbox']

    when:
    def allowed = controls.isBranchAllowed()

    then:
    allowed == expected
    1 * steps.httpRequest(_) >> [content: 'allowlist']
    1 * steps.readYaml([text: 'allowlist']) >> [branches: [[name: 'master', allowed: true], [name: 'disabled', allowed: false]]]

    where:
    skipCheck | branch        | expected
    'false'   | 'test-branch' | false
    null      | 'test-branch' | false
    ''        | 'test-branch' | false
    'yes'     | 'test-branch' | false
    '1'       | 'test-branch' | false
    ' FALSE ' | 'test-branch' | false
    null      | 'master'      | true
    null      | 'disabled'    | false
  }
}
