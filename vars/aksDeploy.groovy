import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.PipelineCallbacks

def call(List templateEnvVars, String subscription, PipelineCallbacks pl, String namespace) {
  withDocker('hmcts/cnp-aks-client:1.2', null) {
    withSubscription(subscription) {

      def keyvaultUrl = "https://${pl.keyvaultName}.vault.azure.net/".replace('(subscription)', subscription)

      wrap([
        $class                   : 'AzureKeyVaultBuildWrapper',
        azureKeyVaultSecrets     : pl.vaultSecrets,
        keyVaultURLOverride      : keyvaultUrl,
        applicationIDOverride    : env.AZURE_CLIENT_ID,
        applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {
        withEnv(templateEnvVars) {
          def kubectl = new Kubectl(this, subscription, namespace)
          kubectl.login()

          sh "envsubst < src/kubernetes/deployment.tmpl > src/kubernetes/deployment.yaml"
          kubectl.apply('src/kubernetes/deployment.yaml')

          env.AKS_TEST_URL = "http://" + kubectl.getServiceLoadbalancerIP("${env.SERVICE_NAME}-ilb")
          echo "Your AKS service can be reached at: ${env.AKS_TEST_URL}"
        }
      }
    }
  }
}
