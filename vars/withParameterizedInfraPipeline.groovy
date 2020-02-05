import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.pipeline.TeamConfig

def call(String product, String environment, String subscription, Closure body) {
  call(product, environment, subscription, '', body)
}
def call(String product, String environment, String subscription, String deploymentTargets, Closure body) {

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

  def deploymentTargetList = (deploymentTargets) ? deploymentTargets.split(',') as List : null

  node {
    def slackChannel = new TeamConfig(this).getBuildNoticesSlackChannel(product)
    try {
      env.PATH = "$env.PATH:/usr/local/bin"

      stage('Checkout') {
        callbacksRunner.callAround('checkout') {
          checkoutScm()
        }
      }


      sectionInfraBuild(
        pipelineConfig: pipelineConfig,
        subscription: subscription,
        environment: environment,
        deploymentTargets: deploymentTargetList,
        product: product)


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
