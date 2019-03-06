import uk.gov.hmcts.contino.Kubectl

def call(List templateEnvVars, String subscription, String namespace) {
  withAksClient(subscription) {
    withEnv(templateEnvVars) {
      def kubectl = new Kubectl(this, subscription, namespace)
      kubectl.login()

      sh "envsubst < src/kubernetes/deployment.template.yaml > src/kubernetes/deployment.yaml"
      kubectl.delete 'src/kubernetes/deployment.yaml'
    }
  }
}
