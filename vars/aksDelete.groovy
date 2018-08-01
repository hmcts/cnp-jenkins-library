import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.PipelineCallbacks

def call(List templateEnvVars, String subscription, String namespace) {
  withDocker('hmcts/cnp-aks-client:1.2', null) {
    withSubscription(subscription) {
      withEnv(templateEnvVars) {
        def kubectl = new Kubectl(this, subscription, namespace)
        kubectl.login()

        sh "envsubst < src/kubernetes/deployment.tmpl > src/kubernetes/deployment.yaml"
        kubectl.delete 'src/kubernetes/deployment.yaml'
      }
    }
  }
}
