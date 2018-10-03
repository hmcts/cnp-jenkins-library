import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.GithubAPI

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment

  def kubeResourcesDir
  def kubeResourcesDirDefault = "src/kubernetes"
  def kubeResourcesDirAlternate = "kubernetes"

  def digestName = dockerImage.getDigestName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"
  def templateEnvVars = ["NAMESPACE=${aksServiceName}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}", "SERVICE_FQDN=${serviceFqdn}"]

  withEnv(templateEnvVars) {

    def kubectl = new Kubectl(this, subscription, aksServiceName)
    kubectl.login()

    kubectl.createNamespace(env.NAMESPACE)
    kubectl.deleteDeployment(aksServiceName)

    if (fileExists(kubeResourcesDirDefault)) {
      kubeResourcesDir = kubeResourcesDirDefault
    } else if (fileExists(kubeResourcesDirAlternate)) {
      kubeResourcesDir = kubeResourcesDirAlternate
    } else {
      throw new RuntimeException("No Kubernetes resource directory found at $kubeResourcesDirDefault or $kubeResourcesDirAlternate")
    }

    // environment specific config is optional
    def configTemplate = "${kubeResourcesDir}/config.${environment}.yaml"
    if (fileExists(configTemplate)) {
      sh "envsubst < ${configTemplate} > ${kubeResourcesDir}/config.yaml"
      kubectl.apply("${kubeResourcesDir}/config.yaml")
    }

    sh "envsubst < ${kubeResourcesDir}/deployment.template.yaml > ${kubeResourcesDir}/deployment.yaml"
    kubectl.apply("${kubeResourcesDir}/deployment.yaml")

    // Get the IP of the Traefik Ingress Controller
    def ingressIP = kubectl.getServiceLoadbalancerIP("traefik-1", "kube-system")
    registerConsulDns(subscription, env.SERVICE_FQDN, ingressIP)

    env.AKS_TEST_URL = "https://${env.SERVICE_FQDN}"
    echo "Your AKS service can be reached at: ${env.SERVICE_FQDN}"

    addGithubLabels()

    def url = env.AKS_TEST_URL + '/health'
    def healthChecker = new HealthChecker(this)
    healthChecker.check(url, 10, 30)

    return env.AKS_TEST_URL
  }
}

def addGithubLabels() {
  def namespaceLabel   = 'ns:' + env.NAMESPACE
  def labels = [namespaceLabel]

  def githubApi = new GithubAPI(this)
  githubApi.addLabelsToCurrentPR(labels)
}
