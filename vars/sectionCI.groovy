import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.ProjectBranch

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  if (pl.dockerBuild) {
    withSubscription(subscription) {

      def registrySecrets = [
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-host', version: '', envVariable: 'REGISTRY_HOST' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-username', version: '', envVariable: 'REGISTRY_USERNAME' ],
        [ $class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-password', version: '', envVariable: 'REGISTRY_PASSWORD' ]
      ]

      wrap([$class: 'AzureKeyVaultBuildWrapper',
            azureKeyVaultSecrets: registrySecrets,
            keyVaultURLOverride: env.INFRA_VAULT_URL,
            applicationIDOverride    : env.AZURE_CLIENT_ID,
            applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {

        def repository = 'hmcts'
        def serviceName  = product + '-' + component
        def tag = new ProjectBranch(env.BRANCH_NAME).imageTag()
        def imageName = "$REGISTRY_HOST/$repository/$serviceName:$tag"

        stage('Docker Build') {
          pl.callAround('dockerbuild') {
            timeout(time: 15, unit: 'MINUTES') {
              dockerBuild(imageName)
            }
          }
        }
      }
    }
  }
}
