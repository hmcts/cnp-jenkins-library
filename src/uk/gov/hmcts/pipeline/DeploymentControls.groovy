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
    steps.echo "[DeploymentControls] Checking deployment status for: ${repository}"
    def deploymentControls = getDeploymentControls()
    
    steps.echo "[DeploymentControls] YAML contains 'repositories' key: ${deploymentControls.containsKey('repositories')}"
    
    if (!deploymentControls.containsKey('repositories')) {
      steps.echo "[DeploymentControls] No 'repositories' key found, returning false"
      return false
    }

    def repositories = deploymentControls.get('repositories')
    steps.echo "[DeploymentControls] Number of repositories in list: ${repositories.size()}"
    
    def repoEntry = repositories.find { it.repo.equalsIgnoreCase(repository) }
    
    if (repoEntry == null) {
      steps.echo "[DeploymentControls] Repository not found in list, returning false"
      return false
    }
    
    steps.echo "[DeploymentControls] Found repository entry: ${repoEntry}"
    steps.echo "[DeploymentControls] deployment-enabled value: ${repoEntry['deployment-enabled']}"
    
    def result = repoEntry && repoEntry['deployment-enabled'] == true
    steps.echo "[DeploymentControls] Final result: ${result}"
    return result
  }

}
