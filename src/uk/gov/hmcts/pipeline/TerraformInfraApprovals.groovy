package uk.gov.hmcts.pipeline

class TerraformInfraApprovals {

  static final String GITHUB_CREDENTIAL = 'jenkins-github-hmcts-api-token'

  def steps
  def subscription
  def infraApprovals

  TerraformInfraApprovals(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
  }

  def getInfraApprovals() {
    if (infraApprovals == null) {
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: "${GITHUB_CREDENTIAL}",
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/terraform-infra-approvals.json",
        validResponseCodes: '200',
        outputFile: "terraform-infra-approvals.json"
      )
      //infraApprovals = response.content
      infraApprovals = "terraform-infra-approvals.json"
      def infraApprovalsFile = new File(infraApprovals)
      if (!infraApprovalsFile.exists() || infraApprovalsFile.length() <= 0) {
        infraApprovals = null
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
    return this.steps.sh(returnStatus: true, script: "tf-utils --whitelist ${tfInfraPath} ${infraApprovals}")
  }

}
