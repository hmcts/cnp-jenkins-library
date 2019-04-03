import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.TeamNames

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment
  def aksEnvironment = params.environment
  def product = params.product
  def component = params.component

  def helmResourcesDir = Helm.HELM_RESOURCES_DIR

  def imageName = dockerImage.getTaggedName()
  def aksServiceName = dockerImage.getAksServiceName()
  def aksDomain = "${(subscription in ['nonprod', 'prod']) ? 'service.core-compute-preview.internal' : 'service.core-compute-saat.internal'}"
  def serviceFqdn = "${aksServiceName}.${aksDomain}"

  def consul = new Consul(this)
  def consulApiAddr = consul.getConsulIP()

  def kubectl = new Kubectl(this, subscription, aksServiceName)
  kubectl.login()
  // Get the IP of the Traefik Ingress Controller
  def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "admin")

  def templateEnvVars = [
    "NAMESPACE=${aksServiceName}",
    "SERVICE_NAME=${aksServiceName}",
    "IMAGE_NAME=${imageName}",
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
    if (fileExists(defaultValues)) {
      onPR {
        aksEnvironment = "preview"
      }
    } else {
      echo '''
================================================================================

 ____      ____  _       _______     ____  _____  _____  ____  _____   ______  
|_  _|    |_  _|/ \\     |_   __ \\   |_   \\|_   _||_   _||_   \\|_   _|.' ___  | 
  \\ \\  /\\  / / / _ \\      | |__) |    |   \\ | |    | |    |   \\ | | / .'   \\_| 
   \\ \\/  \\/ / / ___ \\     |  __ /     | |\\ \\| |    | |    | |\\ \\| | | |   ____ 
    \\  /\\  /_/ /   \\ \\_  _| |  \\ \\_  _| |_\\   |_  _| |_  _| |_\\   |_\\ `.___]  |
     \\/  \\/|____| |____||____| |___||_____|\\____||_____||_____|\\____|`._____.' 
                                                                               

Provide values.yaml with the chart. Builds will start failing without values.yaml in the near future. 
================================================================================
'''
    }
    if (!fileExists(templateValues) && !fileExists(defaultValues)) {
      throw new RuntimeException("No default values file found at ${templateValues} or ${defaultValues}")
    }
    if (fileExists(templateValues)) {
      sh "envsubst < ${templateValues} > ${defaultValues}"
    }
    values << defaultValues

    // environment specific values is optional
    def valuesEnvTemplate = "${helmResourcesDir}/${chartName}/values.${aksEnvironment}.template.yaml"
    def valuesEnv = "${helmResourcesDir}/${chartName}/values.${aksEnvironment}.yaml"
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

    // if PR delete first as too many people get caught by the error Helm throws if
    // an upgrade is run when there have only been failed deployments
    def deleted = false
    if (new ProjectBranch(this.env.BRANCH_NAME).isPR() &&
      helm.exists(dockerImage.imageTag, namespace) &&
      !helm.hasAnyDeployed(dockerImage.imageTag, namespace)) {

      deleted = true
      helm.delete(dockerImage.getTag())
    }

    // When deleting we might need to wait as some deprovisioning operations are async (i.e. osba)
    def attempts = 1
    while (attempts < 4) {
      try {
        helm.installOrUpgrade(dockerImage.getTag(), values, options)
        echo "Install/upgrade completed(${attempts})."
        break
      } catch (upgradeError) {
        if (!deleted || attempts >= 3) {
          throw upgradeError
        }
        // Clean up the latest install/upgrade attempt
        helm.delete(dockerImage.getTag())
        sleep(attempts * 60)
        attempts++
        echo "Not ready to run install/upgrade [${upgradeError}]. Retrying(${attempts})..."
      }
    }

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
