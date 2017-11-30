package uk.gov.hmcts.contino

import spock.lang.Specification

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

}
