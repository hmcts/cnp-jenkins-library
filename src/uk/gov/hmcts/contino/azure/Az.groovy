package uk.gov.hmcts.contino.azure

import uk.gov.hmcts.pipeline.AgentSelector

class Az {

  def steps
  def subscription
  def az
  Set<String> loggedInAzureConfigNames = [] as Set

  Az(steps, subscription) {
    this.steps = steps
    this.subscription = subscription
  }

  def az(cmd) {
      String azureConfigName = resolveAzureConfigName()
      loginWithEnvironmentManagedIdentityIfRequired(azureConfigName)
      return steps.sh(label: "az ${cmd}", script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${azureConfigName} az ${cmd}", returnStdout: true)?.trim()
  }

  String resolveAzureConfigName() {
    if (usesEnvironmentManagedIdentity()) {
      return AgentSelector.normaliseEnvironment(steps.env.DEPLOYMENT_ENVIRONMENT)
    }

    return this.subscription
  }

  boolean usesEnvironmentManagedIdentity() {
    return AgentSelector.isRunningOnEnvironmentAgent(steps.env)
  }

  void loginWithEnvironmentManagedIdentityIfRequired(String azureConfigName) {
    if (usesEnvironmentManagedIdentity() && !loggedInAzureConfigNames.contains(azureConfigName)) {
      steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${azureConfigName} az login --identity", returnStdout: true)
      loggedInAzureConfigNames.add(azureConfigName)
    }
  }
}
