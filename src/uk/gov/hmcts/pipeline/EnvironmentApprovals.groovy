package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.RateLimitedGithub

class EnvironmentApprovals {
  def steps
  static def environmentApprovals

  EnvironmentApprovals(steps) {
    this.steps = steps
  }

  def getEnvironmentApprovals() {
    if (environmentApprovals == null) {
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      def response = new RateLimitedGithub(steps).getWithDistributedCredentials(steps.env.GIT_CREDENTIALS_ID, [
        consoleLogResponseBody: true,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/${repo}/master/environment-approvals.yml",
        validResponseCodes: '200'
      ])
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
