package uk.gov.hmcts.pipeline

class DeploymentControls {
  def steps
  static def deploymentControls

  DeploymentControls(steps) {
    this.steps = steps
  }

  def getConfigRepoUrl() {
    // this variable is not set in CNP-Flux-Config so set it by default
    // For SDS this will be set to sds-jenkins-config
    // https://github.com/hmcts/sds-flux-config/blob/a5c7deaccf6d07fb7960feb6bc2fb91650422fd3/apps/jenkins/jenkins/ptl/jenkins.yaml#L122
    def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

    return "https://raw.githubusercontent.com/hmcts/${repo}/master/deployment-controls.yml"
  }

  def getDeploymentControls() {
    if (deploymentControls == null) {
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: steps.env.GIT_CREDENTIALS_ID,
        timeout: 10,
        url: getConfigRepoUrl(),
        validResponseCodes: '200'
      )
      deploymentControls = steps.readYaml(text: response.content)
    }
    return deploymentControls
  }

  boolean isDeployEnabled(String repository, def pipelineConfig = null) {
    boolean isJavaLibrary = pipelineConfig?.isJavaLibrary == true

    if (isJavaLibrary) {
      return false
    }
    def deploymentControls = getDeploymentControls()
    if (!deploymentControls.containsKey('repositories')) {

      steps.echo "No 'repositories' key found in deployment controls configuration. Deployment will be disabled by default. Contact Platform Operations team."
      return false
    }

    def repositories = deploymentControls.get('repositories')

    def repoEntry = repositories.find { it.repo.equalsIgnoreCase(repository) }
    def deploymentEnabled = repoEntry && repoEntry['deployment-enabled'] == true

    if (!deploymentEnabled) {
      steps.echo '''
       ================================================================================
       ____      ____  _       _______     ____  _____  _____  ____  _____   ______
       |_  _|    |_  _|/ \\     |_   __ \\   |_   \\|_   _||_   _||_   \\|_   _|.' ___  |
         \\ \\  /\\  / / / _ \\      | |__) |    |   \\ | |    | |    |   \\ | | / .'   \\_|
         \\ \\/  \\/ / / ___ \\     |  __ /     | |\\ \\| |    | |    | |\\ \\| | | |   ____
           \\  /\\  /_/ /   \\ \\_  _| |  \\ \\_  _| |_\\   |_  _| |_  _| |_\\   |_\\ `.___]  |
           \\/  \\/|____| |____||____| |___||_____|\\____||_____||_____|\\____|`._____.'
      '''

      steps.echo """
        Repo ${steps.env.GIT_URL} is not approved for deployment stages.
        Make sure to add your repository to:
        - ${getConfigRepoUrl()}
        
        If you recently updated deployment controls and this is unexpected ensure you are using a new agent as this can be cached.
        ================================================================================
      """
    }

    return repoEntry && repoEntry['deployment-enabled'] == true
  }
}