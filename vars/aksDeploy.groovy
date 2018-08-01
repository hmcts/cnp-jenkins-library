import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.PipelineCallbacks

def call(List templateEnvVars, String subscription, PipelineCallbacks pl, String namespace) {
  withDocker('hmcts/cnp-aks-client:1.2', null) {

    echo 'Logging into subscription...'
    withSubscription(subscription) {

      echo 'Constructing keyvault URL'
      def keyvaultUrl = "https://${pl.keyvaultName}.vault.azure.net/".replace('(subscription)', subscription)
      echo keyvaultUrl

      wrap([
        $class                   : 'AzureKeyVaultBuildWrapper',
        azureKeyVaultSecrets     : pl.vaultSecrets,
        keyVaultURLOverride      : keyvaultUrl,
        applicationIDOverride    : env.AZURE_CLIENT_ID,
        applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {

        //def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
        //az 'aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster'

        echo 'wrapping template variables...'
        withEnv(templateEnvVars) {
          echo 'logging into AKS...'

          def kubectl = new Kubectl(this, subscription, namespace)

          echo 'replacing template variables...'
          sh "envsubst < src/kubernetes/deployment.tmpl > src/kubernetes/deployment.yaml"

          echo 'applying k8s template...'
          kubectl.apply 'src/kubernetes/deployment.yaml'
        }
      }
    }
  }
}
