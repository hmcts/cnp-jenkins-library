import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.pipeline.AKSSubscriptions
import uk.gov.hmcts.pipeline.TeamConfig

def call(type, String product, String component, String environment, String subscription, Closure body) {
  call(type, product,component,environment,subscription,'',body)
}

def call(type, String product, String component, String environment, String subscription, String deploymentTargets, Closure body) {
  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, component),
    nodejs: new NodePipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component)
  ]

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  Builder builder = pipelineType.builder
  def pactBrokerUrl = (new Environment(env)).pactBrokerUrl
  Subscription metricsSubscription = new Subscription(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, metricsSubscription.prodName)
  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(this, callbacks, pipelineConfig)

  body.delegate = dsl
  body.call() // register callbacks

  dsl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  def deploymentTargetList = deploymentTargets.split(',') as List
  AKSSubscriptions aksSubscriptions = new AKSSubscriptions(this)

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      stageWithAgent('Checkout', product) {
        checkoutScm(pipelineCallbacksRunner: callbacksRunner)
      }

      stageWithAgent("Build", product) {
        builder.setupToolVersion()

        callbacksRunner.callAround('build') {
          builder.build()
        }
      }

      sectionDeployToEnvironment(
        appPipelineConfig: pipelineConfig,
        pipelineCallbacksRunner: callbacksRunner,
        pipelineType: pipelineType,
        subscription: subscription,
        aksSubscription: aksSubscriptions.aat,
        environment: environment,
        product: product,
        component: component,
        deploymentTargets: deploymentTargetList,
        pactBrokerUrl: pactBrokerUrl,
        tfPlanOnly: false
      )
    } catch (err) {
      currentBuild.result = "FAILURE"

      notifyBuildFailure channel: slackChannel

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      notifyPipelineDeprecations(slackChannel, metricsPublisher)
      if (env.KEEP_DIR_FOR_DEBUGGING != "true") {
        deleteDir()
      }
    }

    notifyBuildFixed channel: slackChannel

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
