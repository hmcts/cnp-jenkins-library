import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.Docker
import uk.gov.hmcts.contino.DockerImage

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  if (pl.dockerBuild) {
    withSubscription(subscription) {

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

        stage('Docker Build') {
          pl.callAround('dockerbuild') {
            timeout(time: 15, unit: 'MINUTES') {
              def docker = new Docker(this)
              docker.login(env.REGISTRY_HOST, env.REGISTRY_USERNAME, env.REGISTRY_PASSWORD)
              docker.build(dockerImage)
              docker.push(dockerImage)
            }
          }
        }
        def aksUrl
        if (pl.deployToAKS) {
          stage('Deploy to AKS') {
            pl.callAround('aksdeploy') {
              timeout(time: 15, unit: 'MINUTES') {
                aksUrl = aksDeploy(dockerImage, subscription, pl)
                log.info("deployed component URL: ${aksUrl}")
              }
            }
          }
        }
      }
    }
  }
}
