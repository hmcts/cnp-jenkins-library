package uk.gov.hmcts.pipeline

class EnvironmentApprovals {
  def steps
  static final String GITHUB_CREDENTIAL = 'jenkins-github-hmcts-api-token'
  static def environmentApprovals

  EnvironmentApprovals(steps) {
    this.steps = steps
  }

  def getEnvironmentApprovals() {
    if (environmentApprovals == null) {
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: "${GITHUB_CREDENTIAL}",
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/environment-approvals.yml",
        validResponseCodes: '200'
      )
      environmentApprovals = steps.readYaml(text: response.content)
    }
    return environmentApprovals
  }

  boolean isApproved(String environment, String repository) {
    def environmentApprovals = getEnvironmentApprovals()
    if (!environmentApprovals.containsKey(environment)) {
      // if environment is not listed in config then assume no whitelist required
      return true
    }

    def approvals = environmentApprovals.get(environment)
    return approvals.findResult(false) { it.repo.equalsIgnoreCase(repository) ? true : null }
  }

}
