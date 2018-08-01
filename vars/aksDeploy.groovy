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
        // TODO put this somewhere else that's reusable
        // TODO remove hardcoded az config file
        def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
        az 'aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster'

        withEnv(templateEnvVars) {
          sh "envsubst < src/kubernetes/deployment.tmpl > src/kubernetes/deployment.yaml"

          def kubectl = new Kubectl(this, namespace)
          kubectl.apply 'src/kubernetes/deployment.yaml'
        }
      }
    }
  }
}
