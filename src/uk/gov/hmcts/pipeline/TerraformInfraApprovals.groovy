package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.RepositoryUrl

class TerraformInfraApprovals {

  static final String GITHUB_CREDENTIAL = 'jenkins-github-hmcts-api-token'
  static final String GITHUB_BASE_URL = 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/terraform-infra-approvals'

  def steps
  def subscription
  def infraApprovals

  TerraformInfraApprovals(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.infraApprovals = []
  }

  def getInfraApprovals() {
    if (!infraApprovals) {
      String repositoryShortUrl = new RepositoryUrl().getShortWithoutOrg(this.steps.env.CHANGE_URL)
      ["global.json", "${repositoryShortUrl}.json"].each {
        steps.httpRequest(
          consoleLogResponseBody: true,
          authentication: "${GITHUB_CREDENTIAL}",
          timeout: 10,
          url: "${GITHUB_BASE_URL}/${it}",
          validResponseCodes: '200:404',
          outputFile: "${it}"
        )
        def infraApprovalsFile = new File(it)
        if (infraApprovalsFile.exists() && infraApprovalsFile.length() > 0) {
          infraApprovals << it
        }
      }
    }

    return infraApprovals
  }

  boolean isApproved(String tfInfraPath) {
    def infraApprovals = getInfraApprovals()
    if (!infraApprovals) {
      this.steps.sh("echo 'WARNING: No Terraform infrastructure whitelist (terraform-infra-approvals.json) found.'")
      return true
    }
    if (this.subscription == "sandbox") {
      this.steps.sh("echo 'WARNING: Terraform whitelisting disabled. Please do not forget to check your infrastructure.'")
      return true
    }
    return this.steps.sh(returnStatus: true, script: "tf-utils --whitelist ${tfInfraPath} ${infraApprovals.join(" ")}")
  }

}
