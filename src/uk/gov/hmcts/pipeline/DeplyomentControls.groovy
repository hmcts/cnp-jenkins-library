package uk.gov.hmcts.pipeline

class DeploymentControls {
  def steps
  static def deploymentControls

  DeploymentControls(steps) {
    this.steps = steps
  }

  def getDeploymentControls() {
    if (deploymentControls == null) {
      // this variable is not set in CNP-Flux-Config so set it by default
      // For SDS this will be set to sds-jenkins-config
      // https://github.com/hmcts/sds-flux-config/blob/a5c7deaccf6d07fb7960feb6bc2fb91650422fd3/apps/jenkins/jenkins/ptl/jenkins.yaml#L122
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: steps.env.GIT_CREDENTIALS_ID,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/${repo}/master/deployment-controls.yml",
        validResponseCodes: '200'
      )
      deploymentControls = steps.readYaml(text: response.content)
    }
    return deploymentControls
  }

  boolean isDeployEnabled(String repository) {
    def deploymentControls = getDeploymentControls()
    if (!deploymentControls.containsKey('repositories')) {

      steps.echo "No 'repositories' key found in deployment controls configuration. Deployment will be disabled by default. Contact Platform Operations team."
      return false
    }

    def repositories = deploymentControls.get('repositories')

    def repoEntry = repositories.find { it.repo.equalsIgnoreCase(repository) }

    return repoEntry && repoEntry['deployment-enabled'] == true
  }
}