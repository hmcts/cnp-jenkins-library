import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.TeamNames

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component

  def helmResourcesDir = "charts"

  def digestName = dockerImage.getDigestName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"
  def templateEnvVars = ["SERVICE_NAME=${aksServiceName}", "IMAGE_NAME=${digestName}", "SERVICE_FQDN=${serviceFqdn}"]
  
  withEnv(templateEnvVars) {

    def kubectl = new Kubectl(this, subscription, aksServiceName)
    kubectl.login()

    def helm = new Helm(this)
    helm.setup()

    if (!fileExists("${helmResourcesDir}")) {
      throw new RuntimeException("No Helm charts directory found at ${helmResourcesDir}")
    }

    def values = []
    def chart = aksServiceName
    def chartPath = "${product}-${component}"
    def namespace = new TeamNames().getNameNormalizedOrThrow(product)

    // Set some base values so that users can avoid having too many env vars in their values files.
    // We create a new file and pass it first as to allow overriding these
    def baseValues = new File("${helmResourcesDir}/${chartPath}/_values.${aksServiceName}.yaml")
    baseValues.write("")
    ["java", "nodejs"].each {app ->
      baseValues << "${app}:\n"
      baseValues << "  image: ${digestName}\n"
      baseValues << "  ingressHost: ${serviceFqdn}\n"
      baseValues << "\n"
    }
    values << baseValues

    // default values + overrides
    def templateValues = "${helmResourcesDir}/${chartPath}/values.template.yaml"
    def defaultValues = "${helmResourcesDir}/${chartPath}/values.yaml"
    if (!fileExists(templateValues)) {
      throw new RuntimeException("No default values template file found at.")
    }
    sh "envsubst < ${templateValues} > ${defaultValues}"
    values << defaultValues

    // environment specific values is optional
    def valuesEnvTemplate = "${helmResourcesDir}/${chartPath}/values.${environment}.template.yaml"
    def valuesEnv = "${helmResourcesDir}/${chartPath}/values.${environment}.yaml"
    if (fileExists(valuesEnvTemplate)) {
      sh "envsubst < ${valuesEnvTemplate} > ${valuesEnv}"
      values << valuesEnv
    }

    def requirementsEnv = "${helmResourcesDir}/${chartPath}/requirements.${environment}.yaml"
    def requirements = "${helmResourcesDir}/${chartPath}/requirements.yaml"
    if (fileExists(requirementsEnv)) {
      sh "envsubst < ${requirementsEnv} > ${requirements}"
    }

    def options = ["--set product=${product},component=${component}", "--namespace ${namespace}" ]
    helm.installOrUpgrade("${helmResourcesDir}/${chartPath}", chart, values, options)

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
