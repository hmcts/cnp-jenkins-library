import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.HealthChecker

def call(List templateEnvVars, String subscription, PipelineCallbacks pl, String namespace) {
  withDocker('hmcts/cnp-aks-client:1.2', null) {
    withSubscription(subscription) {

      if (pl.vaultName && pl.vaultSecrets?.size() > 0) {

        def projectKeyvaultName = pl.vaultName + '-' + env.NONPROD_ENVIRONMENT_NAME
        def keyvaultUrl = "https://${projectKeyvaultName}.vault.azure.net/"

        wrap([
          $class                   : 'AzureKeyVaultBuildWrapper',
          azureKeyVaultSecrets     : pl.vaultSecrets,
          keyVaultURLOverride      : keyvaultUrl,
          applicationIDOverride    : env.AZURE_CLIENT_ID,
          applicationSecretOverride: env.AZURE_CLIENT_SECRET
        ]) {
          withEnv(templateEnvVars) {
            deploy(subscription, namespace)
          }
        }
      }
      else {
        deploy(subscription, namespace)
      }
    }
  }
}

def deploy(subscription, namespace) {
  // authenticate with the cluster
  def kubectl = new Kubectl(this, subscription, namespace)
  kubectl.login()

  // create namespace (idempotent)
  kubectl.createNamespace(env.NAMESPACE)

  // perform template variable substitution
  sh "envsubst < src/kubernetes/deployment.template.yaml > src/kubernetes/deployment.yaml"

  // deploy the app
  kubectl.apply('src/kubernetes/deployment.yaml')

  // discover and export the service URL for the next stage
  env.AKS_TEST_URL = "http://" + kubectl.getServiceLoadbalancerIP(env.SERVICE_NAME)
  echo "Your AKS service can be reached at: ${env.AKS_TEST_URL}"

  def url = env.AKS_TEST_URL + '/health'
  def healthChecker = new HealthChecker(this)
  healthChecker.check(url, 5, 10)
}
