package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class TerraformInfraApprovalsTest extends Specification {

  def steps
  def infraApprovals
  def approvalsFileName
  static def response = ["content":
  """{
      "resources": [
        {"type": "azurerm_key_vault_secret"},
        {"type": "azurerm_resource_group"}
      ],
      "module_calls": [
        {"source":  "git@github.com:hmcts/cnp-module-webapp?ref=master"},
        {"source":  "git@github.com:hmcts/cnp-module-postgres?ref=master"}
      ]
    }"""]

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.httpRequest(_) >> response
    steps.env >> [SUBSCRIPTION_NAME: 'aat', GIT_URL: 'https://github.com/hmcts/some-project']
    approvalsFileName = "terraform-infra-approvals.json"
    infraApprovals = new TerraformInfraApprovals(steps)
  }

  def "isApproved() should return true when subscription is sandbox"() {
    infraApprovals.subscription = 'sandbox'
    def tfInfraPath = '.'
    steps.fileExists(_) >> true
    when:
    def approved = infraApprovals.isApproved(tfInfraPath)

    then:
    assertThat(approved).isEqualTo(true)
  }

  def "isApproved() should return true when a terraform approvals list doesn't exist"() {
    def tfInfraPath = '.'
    steps.fileExists(_) >> false
    when:
    def approved = infraApprovals.isApproved(tfInfraPath)

    then:
    assertThat(approved).isEqualTo(true)
  }

  def "hasCachedInfraApprovals() should return true when a terraform approvals list exists"() {
    TerraformInfraApprovals.infraApprovals.add(approvalsFileName)
    steps.fileExists(approvalsFileName) >> true
    when:
    def cached = infraApprovals.hasCachedInfraApprovals()

    then:
    assertThat(cached).isEqualTo(true)
  }

  def "hasCachedInfraApprovals() should return false when a terraform approvals list doesn't exist"() {
    TerraformInfraApprovals.infraApprovals.add(approvalsFileName)
    steps.fileExists(approvalsFileName) >> false
    when:
    def cached = infraApprovals.hasCachedInfraApprovals()

    then:
    assertThat(cached).isEqualTo(false)
  }

}
