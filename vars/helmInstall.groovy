import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.GithubAPI

def call(DockerImage dockerImage, List<String> charts, Map params) {

  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component

  def helmResourcesDir
  def helmResourcesDirDefault = "src/kubernetes/charts"
  def helmResourcesDirAlternate = "kubernetes/charts"

  def digestName = dockerImage.getDigestName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"
  def templateEnvVars = ["NAMESPACE=${aksServiceName}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}", "SERVICE_FQDN=${serviceFqdn}"]

  def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }

  withEnv(templateEnvVars) {

    def kubectl = new Kubectl(this, subscription, aksServiceName)
    kubectl.login()

    kubectl.createNamespace(env.NAMESPACE)

    def helm = new Helm(this)
    helm.init()
    def values = []

    if (fileExists("${helmResourcesDirDefault}/${charts[0]}")) {
      helmResourcesDir = helmResourcesDirDefault
    } else if (fileExists("${helmResourcesDirAlternate}/${charts[0]}")) {
      helmResourcesDir = helmResourcesDirAlternate
    } else {
      throw new RuntimeException("No Helm charts directory found at $helmResourcesDirDefault or $helmResourcesDirAlternate")
    }

    // default values + overrides
    for (chart in charts) {
      def chartValues = []
      def templateValues = "${helmResourcesDir}/${chart}/values.template.yaml"
      def defaultValues = "${helmResourcesDir}/${chart}/values.yaml"
      if (!fileExists(templateValues)) {
        throw new RuntimeException("No default values template file found at.")
      }
      sh "envsubst < ${templateValues} > ${defaultValues}"
      chartValues << defaultValues

      // environment specific values is optional
      def valuesEnvTemplate = "${helmResourcesDir}/${chart}/values.${environment}.template.yaml"
      def valuesEnv = "${helmResourcesDir}/${chart}/values.${environment}.yaml"
      if (fileExists(valuesEnvTemplate)) {
        sh "envsubst < ${valuesEnvTemplate} > ${valuesEnv}"
        chartValues << valuesEnv
      }
      values << chartValues
    }

    az "configure --defaults acr=hmcts"
    az "acr helm repo add  --subscription ${subscription}"
    def options = ["--set", "product=${product}", "component=${component}"]
    helm.installOrUpgradeMulti(charts, values, options)

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
