import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.GithubAPI

def call(DockerImage dockerImage, Map params, String... charts) {

  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component

  def helmResourcesDir = "charts"

  def digestName = dockerImage.getDigestName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"
  def templateEnvVars = ["NAMESPACE=${aksServiceName}", "SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}", "SERVICE_FQDN=${serviceFqdn}"]
  
  withEnv(templateEnvVars) {

    def kubectl = new Kubectl(this, subscription, aksServiceName)
    kubectl.login()

    //kubectl.createNamespace(env.NAMESPACE)

    def helm = new Helm(this)
    helm.init()
    def values = []

    if (charts == null) {
      charts = [] as String[]
    }
    if (!charts.contains(aksServiceName)) {
      charts = charts + aksServiceName
    }

    if (!fileExists("${helmResourcesDir}")) {
      throw new RuntimeException("No Helm charts directory found at ${helmResourcesDir}")
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

      def requirementsEnv = "${helmResourcesDir}/${chart}/requirements.${environment}.yaml"
      def requirements = "${helmResourcesDir}/${chart}/requirements.yaml"
      if (fileExists(requirementsEnv)) {
        sh "envsubst < ${requirementsEnv} > ${requirements}"
      }
    }

    def options = ["--set product=${product},component=${component}", "--namespace ${product}" ]
    helm.installOrUpgradeMulti(helmResourcesDir, charts as List, values, options)

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
