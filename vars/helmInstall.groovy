import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.TeamNames
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AKSSubscription


def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment
  def templateOverrideEnvironment = params.environment
  def helmOptionEnvironment = params.environment
  def product = params.product
  def component = params.component
  AppPipelineConfig config = params.appPipelineConfig

  def helmResourcesDir = Helm.HELM_RESOURCES_DIR

  def imageName = dockerImage.getTaggedName()
  def aksServiceName = dockerImage.getAksServiceName()
  def serviceFqdn = "${aksServiceName}.service.core-compute-${environment}.internal"

  def consul = new Consul(this, environment)
  def consulApiAddr = consul.getConsulIP()

  def kubectl = new Kubectl(this, subscription, aksServiceName, params.aksSubscription)
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
    if(params.aksSubscription == new AKSSubscription(this.env).aatName) {
      helm.enableTLS(params.aksSubscription, this.env.AAT_TLS_KEY_VAULT)
    }


    // default values + overrides
    def templateValues = "${helmResourcesDir}/${chartName}/values.template.yaml"
    def defaultValues = "${helmResourcesDir}/${chartName}/values.yaml"
    if (! fileExists(defaultValues)) {

      onPR {
        templateOverrideEnvironment = new Environment(env).nonProdName
      }
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
    def valuesEnvTemplate = "${helmResourcesDir}/${chartName}/values.${templateOverrideEnvironment}.template.yaml"
    def valuesEnv = "${helmResourcesDir}/${chartName}/values.${templateOverrideEnvironment}.yaml"
    if (fileExists(valuesEnvTemplate)) {
      sh "envsubst < ${valuesEnvTemplate} > ${valuesEnv}"
      values << valuesEnv
    }

    def requirementsEnv = "${helmResourcesDir}/${chartName}/requirements.${environment}.yaml"
    def requirements = "${helmResourcesDir}/${chartName}/requirements.yaml"
    if (fileExists(requirementsEnv)) {
      sh "envsubst < ${requirementsEnv} > ${requirements}"
    }

    onPR {
      helmOptionEnvironment = new Environment(env).nonProdName
    }

    def options = [
      "--set global.subscriptionId=${this.env.AZURE_SUBSCRIPTION_ID} ",
      "--set global.tenantId=${this.env.AZURE_TENANT_ID} ",
      "--set global.environment=${helmOptionEnvironment} ",
      "--set global.enableKeyVaults=true",
      "--namespace ${namespace}"
    ]

    if (!config.serviceApp) {
      //Forcing Jobs deployed through Jenkins to be Job to avoid cronJobs being run forever.
      options.add("--set global.job.kind=Job")
      //deleting non service apps before installing as K8s doesn't allow editing image of deployed Jobs
      if(helm.exists(dockerImage.getImageTag(), namespace)){
        helm.delete(dockerImage.getImageTag())
      }
    }

    // Helm throws error if trying to upgrade , when there have only been failed deployments
    def deleted = false
    if (helm.exists(dockerImage.getImageTag(), namespace) &&
      !helm.hasAnyDeployed(dockerImage.getImageTag(), namespace)) {

      deleted = true
      helm.delete(dockerImage.getImageTag())
    }

    // When deleting we might need to wait as some deprovisioning operations are async (i.e. osba)
    def attempts = 1
    while (attempts < 4) {
      try {
        helm.installOrUpgrade(dockerImage.getImageTag(), values, options)
        echo "Install/upgrade completed(${attempts})."
        break
      } catch (upgradeError) {
        if (!deleted || attempts >= 3) {
          throw upgradeError
        }
        // Clean up the latest install/upgrade attempt
        helm.delete(dockerImage.getImageTag())
        sleep(attempts * 60)
        attempts++
        echo "Not ready to run install/upgrade [${upgradeError}]. Retrying(${attempts})..."
      }
    }

    onPR {
      if (subscription != 'sandbox') {
        addGithubLabels()
      }
    }

    if (config.serviceApp) {
      // Register service dns
      consul.registerDns(aksServiceName, ingressIP)

      env.AKS_TEST_URL = "https://${env.SERVICE_FQDN}"
      echo "Your AKS service can be reached at: https://${env.SERVICE_FQDN}"

      def url = env.AKS_TEST_URL + '/health'
      def healthChecker = new HealthChecker(this)
      healthChecker.check(url, 10, 40)

      return env.AKS_TEST_URL
    } else {
      return null
    }
  }
}

def addGithubLabels() {
  def namespaceLabel   = 'ns:' + env.NAMESPACE
  def labels = [namespaceLabel]

  def githubApi = new GithubAPI(this)
  githubApi.addLabelsToCurrentPR(labels)
}
