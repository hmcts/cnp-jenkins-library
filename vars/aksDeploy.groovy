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

          // authenticate with the cluster
          def kubectl = new Kubectl(this, subscription, namespace)
          kubectl.login()

          // create namespace (idempotent)
          kubectl.createNamespace(env.NAMESPACE)

          // perform template variable substitution
          sh "envsubst < src/kubernetes/deployment.tmpl > src/kubernetes/deployment.yaml"

          // deploy the app
          kubectl.apply('src/kubernetes/deployment.yaml')

          // discover and export the service URL for the next stage
          env.AKS_TEST_URL = "http://" + kubectl.getServiceLoadbalancerIP(env.SERVICE_NAME)
          echo "Your AKS service can be reached at: ${env.AKS_TEST_URL}"
        }
      }
    }
  }
}
