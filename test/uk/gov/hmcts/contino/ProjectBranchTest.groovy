package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class ProjectBranchTest extends Specification {

  def "constructor should throw an exception when intialized with null input"() {
    when:
    new ProjectBranch(null)

    then:
    thrown(NullPointerException)
  }

  def "isMaster should return true when branch is 'master'"() {
    when:
    def branch = new ProjectBranch('master')

    then:
    branch.isMaster()
  }

  def "isMaster should return true when branch is 'cnp'"() {
    when:
    def branch = new ProjectBranch('cnp')

    then:
    branch.isMaster()
  }

  def "isMaster should return false when branch is not 'master' or 'cnp'"() {
    when:
    def branch = new ProjectBranch('cnp123')

    then:
    !branch.isMaster()
  }

  def "isPR should return true when branch name starts with 'PR'"() {
    when:
    def branch = new ProjectBranch('PRwhatever')

    then:
    branch.isPR()
  }

  def "isPR should return false when branch name does not start with 'PR'"() {
    when:
    def branch = new ProjectBranch('master')

    then:
    !branch.isPR()
  }

  def "imageTag should return a PR tag in LOWER CASE when a PR branch is used"() {
    when:
    def branch = new ProjectBranch('PR-23')

    then:
    assertThat(branch.imageTag()).isEqualTo('pr-23')
  }

  def "imageTag should return branch name if it isn't a PR or master"() {
    when:
    def branch = new ProjectBranch('demo')

    then:
    assertThat(branch.imageTag()).isEqualTo('demo')
  }

  def "imageTag should return 'latest' if branch is master"() {
    when:
    def branch = new ProjectBranch('master')

    then:
    assertThat(branch.imageTag()).isEqualTo('latest')
  }

  def "imageTag should return branch name with no slashes in it"() {
    when:
    def branch = new ProjectBranch('feature/blah')

    then:
    assertThat(branch.imageTag()).isEqualTo('feature-blah')
  }

}
