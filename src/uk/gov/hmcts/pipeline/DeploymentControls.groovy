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

      try {
        def response = steps.httpRequest(
          consoleLogResponseBody: true,
          authentication: steps.env.GIT_CREDENTIALS_ID,
          timeout: 10,
          url: "https://raw.githubusercontent.com/hmcts/${repo}/DTSPO-29801-discovery-deployment-enablement/deployment-controls.yml",
          validResponseCodes: '200,404'
        )
        
        if (response.status == 404) {
          steps.echo "deployment-controls.yml not found - assuming all repositories are deployment-enabled by default"
          deploymentControls = [repositories: []]
          return deploymentControls
        }
        
        deploymentControls = steps.readYaml(text: response.content)
      } catch (Exception e) {
        steps.echo "Error fetching deployment-controls.yml: ${e.message}"
        steps.echo "Assuming all repositories are deployment-enabled by default"
        deploymentControls = [repositories: []]
      }
    }
    return deploymentControls
  }

  boolean isDeployEnabled(String repository) {
    steps.echo "Checking deployment status for repository: ${repository}"
    def deploymentControls = getDeploymentControls()
    
    if (!deploymentControls.containsKey('repositories')) {
      steps.echo "No repositories key found in deployment-controls.yml - deployment enabled by default"
      return true
    }

    def repositories = deploymentControls.get('repositories')
    def repoEntry = repositories.find { it.repo.equalsIgnoreCase(repository) }
    
    if (repoEntry == null) {
      steps.echo "Repository ${repository} not found in deployment-controls.yml - deployment enabled by default"
      return true
    }
    
    def isEnabled = repoEntry['deployment-enabled'] == true
    steps.echo "Repository ${repository} deployment-enabled flag: ${repoEntry['deployment-enabled']} - result: ${isEnabled}"
    return isEnabled
  }

}
