import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.azure.Acr

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  if (pl.dockerBuild) {
    withSubscription(subscription) {

      def registrySecrets = [
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-name', version: '', envVariable: 'REGISTRY_NAME' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-resource-group', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'aks-resource-group', version: '', envVariable: 'AKS_RESOURCE_GROUP' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'aks-cluster-name', version: '', envVariable: 'AKS_CLUSTER_NAME' ],
      ]

      wrap([$class: 'AzureKeyVaultBuildWrapper',
            azureKeyVaultSecrets: registrySecrets,
            keyVaultURLOverride: env.INFRA_VAULT_URL,
            applicationIDOverride    : env.AZURE_CLIENT_ID,
            applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {

        def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
        def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag())

        stage('Docker Build') {
          pl.callAround('dockerbuild') {
            timeout(time: 15, unit: 'MINUTES') {
              acr.build(dockerImage)
            }
          }
        }
        if (pl.deployToAKS) {
          stage('Deploy to AKS') {
            pl.callAround('aksdeploy') {
              timeout(time: 15, unit: 'MINUTES') {
                aksDeploy(dockerImage, params, acr)
              }
            }
          }
        }
      }
    }
  }
}
