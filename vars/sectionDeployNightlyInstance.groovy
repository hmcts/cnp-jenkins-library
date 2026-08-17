import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.pipeline.AKSSubscriptions

def call(Map params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  def pipelineType = params.pipelineType
  String product = params.product
  String component = params.component

  def environment = new Environment(env)
  def subscription = new Subscription(env)
  def aksSubscriptions = new AKSSubscriptions(this)
  def target = resolveTarget(config.nightlyDeploymentEnvironment, subscription, environment, aksSubscriptions)
  String imageTag = config.nightlyDeploymentImageTag
  def dockerImage

  stageWithAgent("Nightly Docker Build", product) {
    if (!fileExists('Dockerfile')) {
      throw new RuntimeException("Nightly deployment requires a Dockerfile")
    }

    withAcrClient(target.subscription) {
      def imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
      def acr = new Acr(this, target.subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
      dockerImage = new DockerImage(product, component, acr, imageTag, env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)

      pcr.callAround('nightlydockerbuild') {
        timeoutWithMsg(time: 80, unit: 'MINUTES', action: 'Nightly Docker Build') {
          if (fileExists('acb.tpl.yaml')) {
            acr.runWithTemplate('acb.tpl.yaml', dockerImage)
          } else {
            acr.build(dockerImage)
          }
          acr.retagForStage(DockerImage.DeploymentStage.NIGHTLY, dockerImage)
          acr.purgeOldTags(DockerImage.DeploymentStage.NIGHTLY, dockerImage)
        }
      }
    }
  }

  def deployParams = [
    appPipelineConfig: config,
    pipelineCallbacksRunner: pcr,
    pipelineType: pipelineType,
    subscription: target.subscription,
    aksSubscription: target.aksSubscription,
    environment: target.environment,
    valuesEnvironment: config.nightlyDeploymentValuesEnvironment,
    helmOptionEnvironment: config.nightlyDeploymentValuesEnvironment,
    product: product,
    component: component
  ]

  def aksUrl
  lock("${product}-${component}-${target.environment}-${imageTag}-nightly-deploy") {
    stageWithAgent("Nightly AKS deploy - ${target.environment}", product) {
      withTeamSecrets(config, target.environment) {
        pcr.callAround('nightlyakschartsinstall') {
          withAksClient(target.subscription, target.environment, product) {
            timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'Install Nightly Charts to AKS') {
              def helmEnvironment = deployParams.environment.replace('idam-', '')
              deployParams.environment = helmEnvironment
              log.info("Using AKS environment: ${helmEnvironment}")
              warnAboutDeprecatedChartConfig(product: product, component: component, repoUrl: (env.GIT_URL ?: 'unknown'))
              aksUrl = helmInstall(dockerImage, deployParams)
              log.info("deployed nightly component URL: ${aksUrl}")
            }
          }
        }
      }
    }
  }

  return [
    url: aksUrl,
    dockerImage: dockerImage,
    deployParams: deployParams,
    environment: target.environment,
    subscription: target.subscription
  ]
}

private Map resolveTarget(String targetName, Subscription subscription, Environment environment, AKSSubscriptions aksSubscriptions) {
  switch ((targetName ?: 'preview').toLowerCase()) {
    case 'preview':
      return [environment: environment.previewName, subscription: subscription.previewName, aksSubscription: aksSubscriptions.preview]
    case 'aat':
    case 'nonprod':
      return [environment: environment.nonProdName, subscription: subscription.nonProdName, aksSubscription: aksSubscriptions.aat]
    case 'demo':
      return [environment: environment.demoName, subscription: subscription.demoName, aksSubscription: aksSubscriptions.demo]
    case 'perftest':
      return [environment: environment.perftestName, subscription: subscription.perftestName, aksSubscription: aksSubscriptions.perftest]
    case 'ithc':
      return [environment: environment.ithcName, subscription: subscription.ithcName, aksSubscription: aksSubscriptions.ithc]
    default:
      throw new IllegalArgumentException("Unsupported nightly deployment environment: ${targetName}")
  }
}
