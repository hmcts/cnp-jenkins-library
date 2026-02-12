package uk.gov.hmcts.pipeline

class DeploymentControls {
  def steps
  static def deploymentControls

  DeploymentControls(steps) {
    this.steps = steps
  }

  def getDeploymentControls() {
    if (deploymentControls == null) {
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: steps.env.GIT_CREDENTIALS_ID,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/${repo}/DTSPO-29801-discovery-deployment-enablement/deployment-controls.yml",
        validResponseCodes: '200'
      )
      deploymentControls = steps.readYaml(text: response.content)
    }
    return deploymentControls
  }

  boolean isDeployEnabled(String repository) {
    def deploymentControls = getDeploymentControls()
    if (!deploymentControls.containsKey('repositories')) {
      return false
    }

    def repositories = deploymentControls.get('repositories')
    def repoEntry = repositories.find { it.repo.equalsIgnoreCase(repository) }
    return repoEntry && repoEntry['deployment-enabled'] == true
  }

}
