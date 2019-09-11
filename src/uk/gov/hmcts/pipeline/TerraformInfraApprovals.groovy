package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.RepositoryUrl

class TerraformInfraApprovals {

  static final String GITHUB_BASE_URL = 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/terraform-infra-approvals'

  public static final String TFUTILS_IMAGE = 'hmctspublic.azurecr.io/tf-utils:dbf0x'
  public static final String TFUTILS_RUN_ARGS = '--entrypoint ""'
  public static final String TFUTILS_WHITELIST_PATH = 'tf-whitelist'

  def steps
  def subscription
  def whitelistPath
  def tfutilsArgs
  static def infraApprovals = []

  TerraformInfraApprovals(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.whitelistPath = "${this.steps.env.WORKSPACE}/${TFUTILS_WHITELIST_PATH}"
    def whitelistDir = new File(this.whitelistPath)
    if (!whitelistDir.exists()) {
      whitelistDir.mkdir()
    }
    this.tfutilsArgs = TFUTILS_RUN_ARGS + " -v ${this.whitelistPath}:/${TFUTILS_WHITELIST_PATH}"
  }

  def getInfraApprovals() {
    if (!hasCachedInfraApprovals()) {
      def localInfraApprovals = []
      String repositoryShortUrl = new RepositoryUrl().getShortWithoutOrgOrSuffix(this.steps.env.GIT_URL)
      ["global.json": "200", "${repositoryShortUrl}.json": "200:404"].each { k,v ->
        def outFile = "${whitelistPath}/${k}"
        def response = steps.httpRequest(
          consoleLogResponseBody: true,
          timeout: 10,
          url: "${GITHUB_BASE_URL}/${k}",
          validResponseCodes: v,
          outputFile: outFile
        )
        if (response.status == 200) {
          this.steps.echo "Infra approvals file exists"
          localInfraApprovals << outFile
        } else {
          this.steps.echo "Infra approvals file ${k} doesn't exist"
        }
      }
      infraApprovals = localInfraApprovals
    }

    return infraApprovals
  }

  def getMappedInfraApprovals() {
    return getInfraApprovals().collect {
      it.replaceFirst("${whitelistPath}", "/${TFUTILS_WHITELIST_PATH}")
    }
  }

  boolean isApproved(String tfInfraPath) {
    infraApprovals = getMappedInfraApprovals()
    if (!infraApprovals) {
      this.steps.sh("echo 'WARNING: No Terraform infrastructure whitelist found.'")
      return true
    }

    def joinedInfraApprovals = infraApprovals.join(" ")
    this.steps.echo(joinedInfraApprovals)
    if (this.subscription == "sandbox") {
      this.steps.sh("echo 'WARNING: Terraform whitelisting disabled in sandbox'")
//      return true
    }
    this.steps.withDocker(TFUTILS_IMAGE, tfutilsArgs) {
      return this.steps.sh(returnStatus: true, script: "/tf-utils --whitelist ${tfInfraPath} ${joinedInfraApprovals}") == 0
    }
  }

  void storeResults(String tfInfraPath) {
    infraApprovals = getMappedInfraApprovals()

    def joinedInfraApprovals = infraApprovals.join(" ")
    this.steps.withDocker(TFUTILS_IMAGE, tfutilsArgs) {
       this.steps.sh(returnStatus: true, script: "/tf-utils --whitelist ${tfInfraPath} ${joinedInfraApprovals} 2> terraform-approvals.log || true")
    }
  }

  boolean hasCachedInfraApprovals() {
    try {
      if (infraApprovals) {
        return infraApprovals.every {
          it.bytes.length > 0
        }
      }
    } catch(e) {
      this.steps.sh("echo 'WARNING: ${e.message}'")
    }  // Do nothing, just return false
    return false
  }

}
