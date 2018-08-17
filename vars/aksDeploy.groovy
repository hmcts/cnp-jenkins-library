import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.azure.Acr

def call(DockerImage dockerImage, Map params, Acr acr) {

  def subscription = params.subscription

  withDocker('hmcts/cnp-aks-client:1.2', null) {
    withSubscription(subscription) {

      PipelineCallbacks pl = params.pipelineCallbacks
      def environment = params.environment

      def keyvaultUrl

      if (pl.vaultSecrets?.size() > 0) {
        if (pl.vaultName) {
          def projectKeyvaultName = pl.vaultName + '-' + environment
          keyvaultUrl = "https://${projectKeyvaultName}.vault.azure.net/"
        }
        else  {
          error "Please set vault name `setVaultName('rhubarb')` if loading vault secrets"
        }
      }

      wrap([
        $class                   : 'AzureKeyVaultBuildWrapper',
        azureKeyVaultSecrets     : pl.vaultSecrets,
        keyVaultURLOverride      : keyvaultUrl,
        applicationIDOverride    : env.AZURE_CLIENT_ID,
        applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {

        def digestName = acr.getImageDigest(dockerImage.getShortName())
        def aksServiceName = dockerImage.getAksServiceName()
        def namespace = dockerImage.product
        def templateEnvVars = ["NAMESPACE=${namespace}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}"]

        withEnv(templateEnvVars) {

          def kubectl = new Kubectl(this, subscription, namespace)
          kubectl.login()

          kubectl.createNamespace(env.NAMESPACE)

          // perform template variable substitution
          sh "envsubst < src/kubernetes/deployment.template.yaml > src/kubernetes/deployment.yaml"

          kubectl.apply('src/kubernetes/deployment.yaml')

          env.AKS_TEST_URL = "http://" + kubectl.getServiceLoadbalancerIP(env.SERVICE_NAME)
          echo "Your AKS service can be reached at: ${env.AKS_TEST_URL}"

          def url = env.AKS_TEST_URL + '/health'
          def healthChecker = new HealthChecker(this)
          healthChecker.check(url, 10, 10)
        }
      }
    }
  }
}
