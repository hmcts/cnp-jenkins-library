import uk.gov.hmcts.pipeline.AgentSelector

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component
  def environment = params.environment

  def reconcile = {
    def azureConfigName = 'jenkins'
    def currentEnvironment = environment ?: env.DEPLOYMENT_ENVIRONMENT
    if (AgentSelector.isRunningOnEnvironmentAgent(env, currentEnvironment, product)) {
      azureConfigName = AgentSelector.normaliseEnvironment(currentEnvironment)
    }

    writeFile file: 'reconcile-flux-image-repository.sh', text: libraryResource('uk/gov/hmcts/flux/reconcile-flux-image-repository.sh')

    try {
      sh """
      export AZURE_CONFIG_DIR=/opt/jenkins/.azure-${azureConfigName}
      az login --identity > /dev/null
      az aks get-credentials --resource-group ${env.PTL_AKS_RESOURCE_GROUP} --name ${env.PTL_AKS_CLUSTER_NAME} --subscription ${env.AKS_PTL_SUBSCRIPTION_NAME} -a --overwrite-existing > /dev/null
      chmod +x reconcile-flux-image-repository.sh
      ./reconcile-flux-image-repository.sh $product $component
      """
    } finally {
      sh 'rm -f reconcile-flux-image-repository.sh'
    }
  }

  if (environment) {
    withEnvironmentAgent(environment, product, reconcile)
  } else {
    reconcile()
  }

}
