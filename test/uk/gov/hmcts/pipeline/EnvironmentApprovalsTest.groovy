package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class EnvironmentApprovalsTest extends Specification {

  def steps
  def environmentApprovals
  static def response = ["content": [
    "prod":[
      ["repo": "https://github.com/hmcts/cnp-plum-recipes-service.git"],
      ["repo": "https://github.com/hmcts/cnp-plum-frontend.git"],
    ]
  ]]
  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.readYaml([text: response.content]) >> response.content
    steps.httpRequest(_) >> response
    steps.env >> [GIT_CREDENTIALS_ID:"test-app-id"]
    environmentApprovals = new EnvironmentApprovals(steps)
  }

  def "isApproved() should return true when an environment approvals list exists, and repo is in whitelist"() {
    def environment = 'prod'
    def repository = 'https://github.com/hmcts/cnp-plum-recipes-service.git'
    def expected = true
    when:
    def approved = environmentApprovals.isApproved(environment, repository)

    then:
    assertThat(approved).isEqualTo(expected)
  }

  def "isApproved() should return false when an environment approvals list exists, and repo is not in whitelist"() {
    def environment = 'prod'
    def repository = 'https://github.com/hmcts/cnp-plum-shared-infrastructure.git'
    def expected = false
    when:
    def approved = environmentApprovals.isApproved(environment, repository)

    then:
    assertThat(approved).isEqualTo(expected)
  }

  def "isApproved() should return true when an environment approvals list doesn't exist"() {
    def environment = 'aat'
    def repository = 'https://github.com/hmcts/cnp-plum-shared-infrastructure.git'
    def expected = true
    when:
    def approved = environmentApprovals.isApproved(environment, repository)

    then:
    assertThat(approved).isEqualTo(expected)
  }
}
