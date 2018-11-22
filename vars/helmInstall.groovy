import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.GithubAPI

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment

  def helmResourcesDir
  def helmResourcesDirDefault = "src/kubernetes/charts"
  def helmResourcesDirAlternate = "kubernetes/charts"

  def digestName = dockerImage.getDigestName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"
  def templateEnvVars = ["NAMESPACE=${aksServiceName}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}", "SERVICE_FQDN=${serviceFqdn}"]

  withEnv(templateEnvVars) {

    def kubectl = new Kubectl(this, subscription, aksServiceName)
    kubectl.login()

    kubectl.createNamespace(env.NAMESPACE)

    def helm = new Helm(this)
    def values = []

    if (fileExists(helmResourcesDirDefault)) {
      helmResourcesDir = helmResourcesDirDefault
    } else if (fileExists(helmResourcesDirAlternate)) {
      helmResourcesDir = helmResourcesDirAlternate
    } else {
      throw new RuntimeException("No Helm charts directory found at $helmResourcesDirDefault or $helmResourcesDirAlternate")
    }

    // default values
    def templateValues = "${helmResourcesDir}/values.template.yaml"
    def defaultValues = "${helmResourcesDir}/values.yaml"
    if (!fileExists(templateValues)) {
      throw new RuntimeException("No default values template file found at.")
    }
    sh "envsubst < ${templateValues} > ${defaultValues}"
    values << defaultValues

    // environment specific values is optional
    def valuesEnv = "${helmResourcesDir}/values.${environment}.template.yaml"
    if (fileExists(valuesEnv)) {
      sh "envsubst < ${valuesEnv} > ${helmResourcesDir}/values.${environment}.yaml"
      values << "${helmResourcesDir}/values.${environment}.yaml"
    }

    helm.installOrUpgrade(values)

    // Get the IP of the Traefik Ingress Controller
    def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "kube-system")
    registerConsulDns(subscription, aksServiceName, ingressIP)

    env.AKS_TEST_URL = "https://${env.SERVICE_FQDN}"
    echo "Your AKS service can be reached at: https://${env.SERVICE_FQDN}"

    if (subscription != 'sandbox') {
      addGithubLabels()
    }

    def url = env.AKS_TEST_URL + '/health'
    def healthChecker = new HealthChecker(this)
    healthChecker.check(url, 10, 40)

    return env.AKS_TEST_URL
  }
}

def addGithubLabels() {
  def namespaceLabel   = 'ns:' + env.NAMESPACE
  def labels = [namespaceLabel]

  def githubApi = new GithubAPI(this)
  githubApi.addLabelsToCurrentPR(labels)
}
