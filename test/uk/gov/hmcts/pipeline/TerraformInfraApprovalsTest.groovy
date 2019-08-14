package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class TerraformInfraApprovalsTest extends Specification {

  def steps
  def infraApprovals
  def approvalsFile
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
    steps.env >> [SUBSCRIPTION_NAME: 'aat', CHANGE_URL: 'https://github.com/hmcts/some-project/pull/68']
    approvalsFile = new File("terraform-infra-approvals.json")
    if (approvalsFile.exists()) {
      approvalsFile.delete()
    }
    infraApprovals = new TerraformInfraApprovals(steps)
  }

  void cleanup() {
    if (approvalsFile.exists()) {
      approvalsFile.delete()
    }
  }

  def "isApproved() should return true when subscription is sandbox"() {
    infraApprovals.subscription = 'sandbox'
    def tfInfraPath = '.'
    approvalsFile << response.content
    when:
    def approved = infraApprovals.isApproved(tfInfraPath)

    then:
    assertThat(approved).isEqualTo(true)
  }

  def "isApproved() should return true when a terraform approvals list doesn't exist"() {
    def tfInfraPath = '.'
    approvalsFile << ""
    when:
    def approved = infraApprovals.isApproved(tfInfraPath)

    then:
    assertThat(approved).isEqualTo(true)
  }

}
