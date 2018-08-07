import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.DockerImage

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  if (pl.dockerBuild) {
    withSubscription(subscription) {

      // TODO required for Docker and Kubectl operations.  Is there a better place than keyvault for this?
      def registrySecrets = [
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-name', version: '', envVariable: 'REGISTRY_NAME' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-host', version: '', envVariable: 'REGISTRY_HOST' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-username', version: '', envVariable: 'REGISTRY_USERNAME' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-password', version: '', envVariable: 'REGISTRY_PASSWORD' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'aks-resource-group', version: '', envVariable: 'AKS_RESOURCE_GROUP' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'aks-cluster-name', version: '', envVariable: 'AKS_CLUSTER_NAME' ],
      ]

      wrap([$class: 'AzureKeyVaultBuildWrapper',
            azureKeyVaultSecrets: registrySecrets,
            keyVaultURLOverride: env.INFRA_VAULT_URL,
            applicationIDOverride    : env.AZURE_CLIENT_ID,
            applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {

        def dockerImage = new DockerImage(product, component, this, new ProjectBranch(env.BRANCH_NAME))
        def buildName = dockerImage.getTaggedName()
        def digestName = dockerImage.getDigestName()
        def aksServiceName = dockerImage.getAksServiceName()

        stage('Docker Build') {
          pl.callAround('dockerbuild') {
            timeout(time: 15, unit: 'MINUTES') {
              dockerBuild(buildName, subscription)
            }
          }
        }
        if (pl.deployToAKS) {
          stage('Deploy to AKS') {
            pl.callAround('aksdeploy') {
              timeout(time: 15, unit: 'MINUTES') {
                def templateEnvVars = ["NAMESPACE=${product}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}"]
                aksDeploy(templateEnvVars, subscription, pl, product)
              }
            }
          }
          /*
          stage('Functional Tests') {
            pl.callAround('functionaltests') {
              timeout(time: 15, unit: 'MINUTES') {
                echo "Running some tests..."
              }
            }
          }
          stage('Delete AKS Deployment') {
            pl.callAround('aksdelete') {
              timeout(time: 15, unit: 'MINUTES') {
                aksDelete(templateEnvVars, subscription, product)
              }
            }
          }
          */
        }
      }
    }
  }
}
