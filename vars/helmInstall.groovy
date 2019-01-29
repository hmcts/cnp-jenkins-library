import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.TeamNames

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component

  def helmResourcesDir = Helm.HELM_RESOURCES_DIR

  def digestName = dockerImage.getDigestName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"

  def consul = new Consul(this)
  def consulApiAddr = consul.getConsulIP()

  def kubectl = new Kubectl(this, subscription, aksServiceName)
  kubectl.login()
  // Get the IP of the Traefik Ingress Controller
  def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "kube-system")

  def templateEnvVars = [
    "NAMESPACE=${aksServiceName}",
    "SERVICE_NAME=${aksServiceName}",
    "IMAGE_NAME=${digestName}",
    "SERVICE_FQDN=${serviceFqdn}",
    "CONSUL_LB_IP=${consulApiAddr}",
    "INGRESS_IP=${ingressIP}"
  ]

  withEnv(templateEnvVars) {

    if (!fileExists("${helmResourcesDir}")) {
      throw new RuntimeException("No Helm charts directory found at ${helmResourcesDir}")
    }

    def values = []
    def chartName = "${product}-${component}"
    def namespace = new TeamNames().getNameNormalizedOrThrow(product)

    def helm = new Helm(this, chartName)
    helm.setup()

    // default values + overrides
    def templateValues = "${helmResourcesDir}/${chartName}/values.template.yaml"
    def defaultValues = "${helmResourcesDir}/${chartName}/values.yaml"
    if (!fileExists(templateValues) && !fileExists(defaultValues)) {
      throw new RuntimeException("No default values file found at ${templateValues} or ${defaultValues}")
    }
    if (fileExists(templateValues)) {
      sh "envsubst < ${templateValues} > ${defaultValues}"
    }
    values << defaultValues

    // environment specific values is optional
    def valuesEnvTemplate = "${helmResourcesDir}/${chartName}/values.${environment}.template.yaml"
    def valuesEnv = "${helmResourcesDir}/${chartName}/values.${environment}.yaml"
    if (fileExists(valuesEnvTemplate)) {
      sh "envsubst < ${valuesEnvTemplate} > ${valuesEnv}"
      values << valuesEnv
    }

    def requirementsEnv = "${helmResourcesDir}/${chartName}/requirements.${environment}.yaml"
    def requirements = "${helmResourcesDir}/${chartName}/requirements.yaml"
    if (fileExists(requirementsEnv)) {
      sh "envsubst < ${requirementsEnv} > ${requirements}"
    }

    def options = [
      "--set global.subscriptionId=${this.env.AZURE_SUBSCRIPTION_ID} ",
      "--set global.tenantId=${this.env.AZURE_TENANT_ID} ",
      "--set global.environment=${environment} ",
      "--namespace ${namespace}"
    ]

    helm.dependencyUpdate()
    helm.lint(values)

    helm.installOrUpgrade(dockerImage.getTag(), values, options)

    // Register service dns
    consul.registerDns(aksServiceName, ingressIP)

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
