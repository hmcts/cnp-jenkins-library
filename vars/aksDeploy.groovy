import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription

  withDocker('hmcts/cnp-aks-client:1.2', null) {
    withSubscription(subscription) {

      def environment = params.environment

      def digestName = dockerImage.getDigestName()
      def aksServiceName = dockerImage.getAksServiceName()
      def namespace = dockerImage.product
      def templateEnvVars = ["NAMESPACE=${namespace}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}"]

      withEnv(templateEnvVars) {

        def kubectl = new Kubectl(this, subscription, namespace)
        kubectl.login()

        kubectl.createNamespace(env.NAMESPACE)
        kubectl.deleteDeployment(aksServiceName)

        // environment specific config is optional
        def configTemplate = "src/kubernetes/config.${environment}.yaml"
        if (fileExists(configTemplate)) {
          sh "envsubst < ${configTemplate} > src/kubernetes/config.yaml"
          kubectl.apply('src/kubernetes/config.yaml')
        }

        sh "envsubst < src/kubernetes/deployment.template.yaml > src/kubernetes/deployment.yaml"
        kubectl.apply('src/kubernetes/deployment.yaml')

        serviceIP = kubectl.getServiceLoadbalancerIP(env.SERVICE_NAME)
        registerConsulDns(subscription, env.SERVICE_NAME, serviceIP)

        env.AKS_TEST_URL = "http://${env.SERVICE_NAME}.${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
        echo "Your AKS service can be reached at: ${env.AKS_TEST_URL}"

        def url = env.AKS_TEST_URL + '/health'
        def healthChecker = new HealthChecker(this)
        healthChecker.check(url, 10, 10)

        return env.AKS_TEST_URL
      }
    }
  }
}
