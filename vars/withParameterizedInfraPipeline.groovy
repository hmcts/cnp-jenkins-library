import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.pipeline.TeamConfig

def call(String product, String environment, String subscription, Closure body) {
  call(product, environment, subscription, false, null, body)
}

def call(String product, String environment, String subscription, String component, Closure body) {
  call(product, environment, subscription, false, component, body)
}
def call(String product, String environment, String subscription, Boolean planOnly, Closure body) {
  call(product, environment, subscription, planOnly, body)
}
def call(String product, String environment, String subscription, Boolean planOnly, String component, Closure body) {

  Subscription metricsSubscription = new Subscription(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "", metricsSubscription.prodName )

  def pipelineConfig = new InfraPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new InfraPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

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


      sectionInfraBuild(
        pipelineConfig: pipelineConfig,
        subscription: subscription,
        environment: environment,
        planOnly: planOnly,
        deploymentTargets: null,
        product: product,
        component: component)


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
