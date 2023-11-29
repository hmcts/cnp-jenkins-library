import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.HealthChecker
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AzPrivateDns
import uk.gov.hmcts.contino.EnvironmentDnsConfig
import uk.gov.hmcts.contino.EnvironmentDnsConfigEntry
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call(DockerImage dockerImage, Map params) {

  def subscription = params.subscription
  def environment = params.environment
  def helmOptionEnvironment = params.environment
  def product = params.product
  def component = params.component
  AppPipelineConfig config = params.appPipelineConfig

  def helmResourcesDir = Helm.HELM_RESOURCES_DIR
  def namespace = env.TEAM_NAMESPACE

  def imageName = dockerImage.getTaggedName()
  def aksServiceName = dockerImage.getAksServiceName()

  EnvironmentDnsConfigEntry dnsConfigEntry = new EnvironmentDnsConfig(this).getEntry(params.environment, product, component)
  AzPrivateDns azPrivateDns = new AzPrivateDns(this, params.environment, dnsConfigEntry)
  String serviceFqdn = azPrivateDns.getHostName(aksServiceName)
  def cnameExists = azPrivateDns.checkForCname(aksServiceName)

  if (serviceFqdn.endsWith(".internal")) {
    WarningCollector.addPipelineWarning("internal_domain", "Usage of `.internal` URLs for preview and AAT Staging is deprecated, please see https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1675868743958669", LocalDate.of(2023, 04, 26))
  }

  def kubectl = new Kubectl(this, subscription, namespace, params.aksSubscription.name)
  kubectl.login()
  // Get the IP of the Traefik Ingress Controller
  def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "admin")

  def templateEnvVars = [
    "NAMESPACE=${namespace}",
    "SERVICE_NAME=${aksServiceName}",
    "IMAGE_NAME=${imageName}",
    "SERVICE_FQDN=${serviceFqdn}",
    "INGRESS_IP=${ingressIP}"
  ]

  withEnv(templateEnvVars) {

    if (!fileExists("${helmResourcesDir}")) {
      throw new RuntimeException("No Helm charts directory found at ${helmResourcesDir}")
    }

    def values = []
    def chartName = "${product}-${component}"

    def helm = new Helm(this, chartName)
    helm.setup()

    // default values + overrides
    def templateValues = "${helmResourcesDir}/${chartName}/values.template.yaml"
    def defaultValues = "${helmResourcesDir}/${chartName}/values.yaml"

    if (!fileExists(defaultValues)) {
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

    onPR {
      def githubApi = new GithubAPI(this)
      for (label in githubApi.getLabelsbyPattern(env.BRANCH_NAME, "pr-values") ) {
        def prLabel = label.minus("pr-values:").replaceAll("\\s","")
        def valuesLabelTemplate = "${helmResourcesDir}/${chartName}/values.${prLabel}.${environment}.template.yaml"
        def valuesLabelEnv = "${helmResourcesDir}/${chartName}/values.${prLabel}.${environment}.yaml"
        if (fileExists(valuesLabelTemplate)) {
          sh "envsubst < ${valuesLabelTemplate} > ${valuesLabelEnv}"
          values << valuesLabelEnv
        }
      }
    }

    def requirementsEnv = "${helmResourcesDir}/${chartName}/requirements.${environment}.yaml"
    def requirements = "${helmResourcesDir}/${chartName}/requirements.yaml"
    if (fileExists(requirementsEnv)) {
      sh "envsubst < ${requirementsEnv} > ${requirements}"
    }

    onPR {
      helmOptionEnvironment = new Environment(env).nonProdName
    }

    def environmentTag = Environment.toTagName(environment)
    
    def options = [
      "--set global.tenantId=${this.env.ARM_TENANT_ID} ",
      "--set global.environment=${helmOptionEnvironment} ",
      "--set global.enableKeyVaults=true",
      "--set global.devMode=true",
      "--set global.tags.teamName=\"${this.env.TEAM_NAME}\"",
      "--set global.tags.applicationName=${this.env.TEAM_APPLICATION_TAG}",
      "--set global.tags.builtFrom=${this.env.GIT_URL}",
      "--set global.tags.businessArea=${this.env.BUSINESS_AREA_TAG}",
      "--set global.tags.environment=${environmentTag}",
      "--set global.disableTraefikTls=${cnameExists}",
      "--namespace ${namespace}"
    ]

    if (!config.serviceApp) {
      //Forcing Jobs deployed through Jenkins to be Job to avoid cronJobs being run forever.
        options.add("--set global.job.kind=Job")
        options.add("--set global.jobKind=Job")
        options.add("--set global.smoketestscron.enabled=false")
        options.add("--set global.functionaltestscron.enabled=false")
      //deleting job for non service apps before installing as K8s doesn't allow editing image/spec of deployed Jobs
      if(kubectl.getJob(dockerImage.getAksServiceName()+"-job") == 0){
        kubectl.deleteJob(dockerImage.getAksServiceName()+"-job")
      }
    }

    // // Helm throws error if trying to upgrade when there have only been failed deployments, or if the previous one is in a 'pending' status
    def deleted = false
    if (helm.hasAnyFailedToDeploy(dockerImage.getImageTag(), namespace)) {

      deleted = true
      helm.delete(dockerImage.getImageTag(), namespace)
      echo "Deleted release for ${dockerImage.getImageTag()} as previous release was not 'deployed' successfully"
    } else {
      echo "Skipping delete for ${dockerImage.getImageTag()} as it doesn't exist or the last version was deployed successfully"
    }

    // When deleting we might need to wait as some deprovisioning operations are async (i.e. osba)
    def attempts = 1
    while (attempts < 4) {
      try {
        helm.installOrUpgrade(dockerImage.getImageTag(), values, options)
        echo "Install/upgrade completed(${attempts})."
        break
      } catch (upgradeError) {
        if (attempts >= 3) {
          if (config.clearHelmReleaseOnFailure) {
            helm.delete(dockerImage.getImageTag(), namespace)
          }
          throw upgradeError
        }
        // Clean up the latest install/upgrade attempt
        helm.delete(dockerImage.getImageTag(), namespace)
        sleep(attempts * 60)
        attempts++
        echo "Not ready to run install/upgrade [${upgradeError}]. Retrying(${attempts})..."
      }
    }

    onPR {
      if (subscription != 'sandbox') {
        addGithubLabels(product)
      }
    }

    if (config.serviceApp) {
      if (!cnameExists) {
        // Register service dns
        azPrivateDns.registerDns(aksServiceName, ingressIP)
      }

      env.AKS_TEST_URL = "https://${env.SERVICE_FQDN}"
      echo "Your AKS service can be reached at: ${env.AKS_TEST_URL}"

      def url = env.AKS_TEST_URL + '/health'
      def healthChecker = new HealthChecker(this)
      healthChecker.check(url, 10, 40)

      return env.AKS_TEST_URL
    } else {
      return null
    }
  }
}

def addGithubLabels(String product) {
  def namespaceLabel = 'ns:' + env.NAMESPACE
  def releaseLabel = 'rel:' + env.SERVICE_NAME
  def productLabel = 'prd:' + product
  def labels = [namespaceLabel, releaseLabel, productLabel]

  def githubApi = new GithubAPI(this)
  githubApi.addLabelsToCurrentPR(labels)
}
