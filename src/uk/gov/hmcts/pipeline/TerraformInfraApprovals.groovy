package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.RepositoryUrl

class TerraformInfraApprovals {

  static final String GITHUB_BASE_URL = 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/terraform-infra-approvals'

  public static final String TFUTILS_IMAGE = 'hmctspublic.azurecr.io/tf-utils:dbf0x'
  public static final String TFUTILS_RUN_ARGS = '--entrypoint ""'

  def steps
  def subscription
  static def infraApprovals = []

  TerraformInfraApprovals(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
  }

  def getInfraApprovals() {
    if (!infraApprovals) {
      def localInfraApprovals = []
      String repositoryShortUrl = new RepositoryUrl().getShortWithoutOrgOrSuffix(this.steps.env.GIT_URL)
      ["global.json": "200", "${repositoryShortUrl}.json": "200:404"].each { k,v ->
        def response = steps.httpRequest(
          consoleLogResponseBody: true,
          timeout: 10,
          url: "${GITHUB_BASE_URL}/${k}",
          validResponseCodes: v,
          outputFile: k
        )
        if (response.status == 200) {
          this.steps.echo "Infra approvals file exists"
          localInfraApprovals << k
        } else {
          this.steps.echo "Infra approvals file ${k} doesn't exist"
        }
      }
      infraApprovals = localInfraApprovals
    }

    return infraApprovals
  }

  boolean isApproved(String tfInfraPath) {
    infraApprovals = getInfraApprovals()
    if (!infraApprovals) {
      this.steps.sh("echo 'WARNING: No Terraform infrastructure whitelist found.'")
      return true
    }

    def joinedInfraApprovals = infraApprovals.join(" ")
    this.steps.echo(joinedInfraApprovals)
    if (this.subscription == "sandbox") {
      this.steps.sh("echo 'WARNING: Terraform whitelisting disabled in sandbox'")
      return true
    }
    this.steps.withDocker(TFUTILS_IMAGE, TFUTILS_RUN_ARGS) {
      return this.steps.sh(returnStatus: true, script: "/tf-utils --whitelist ${tfInfraPath} ${joinedInfraApprovals}") == 0
    }
  }

  void storeResults(String tfInfraPath) {
    infraApprovals = getInfraApprovals()

    def joinedInfraApprovals = infraApprovals.join(" ")
    this.steps.withDocker(TFUTILS_IMAGE, TFUTILS_RUN_ARGS) {
       this.steps.sh(returnStatus: true, script: "/tf-utils --whitelist ${tfInfraPath} ${joinedInfraApprovals} 2> terraform-approvals.log || true")
    }
  }

}
