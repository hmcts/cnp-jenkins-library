package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.RepositoryUrl

class TerraformInfraApprovals {

  static final String GITHUB_BASE_URL = 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/terraform-infra-approvals'

  public static final String TFUTILS_IMAGE = 'tf-utils:dbd9e'
  public static final String TFUTILS_RUN_ARGS = '--entrypoint ""'

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
      String repositoryShortUrl = new RepositoryUrl().getShortWithoutOrg(this.steps.env.GIT_URL)
      ["global.json": "200", "${repositoryShortUrl}.json": "200:404"].each { k,v ->
        steps.httpRequest(
          consoleLogResponseBody: true,
          timeout: 10,
          url: "${GITHUB_BASE_URL}/${k}",
          validResponseCodes: v,
          outputFile: "${k}"
        )
        def infraApprovalsFile = new File(k)
        if (infraApprovalsFile.exists() && infraApprovalsFile.length() > 0) {
          infraApprovals << k
        }
      }
    }

    return infraApprovals
  }

  boolean isApproved(String tfInfraPath) {
    def infraApprovals = getInfraApprovals()
    if (!infraApprovals) {
      this.steps.sh("echo 'WARNING: No Terraform infrastructure whitelist found.'")
      return true
    }
    if (this.subscription == "sandbox") {
      this.steps.sh("echo 'WARNING: Terraform whitelisting disabled. Please do not forget to check your infrastructure.'")
      return true
    }
    this.steps.withDocker(TFUTILS_IMAGE, TFUTILS_RUN_ARGS) {
      return this.steps.sh(returnStatus: true, script: "/tf-utils --whitelist ${tfInfraPath} ${infraApprovals.join(" ")}")
    }
  }

}
