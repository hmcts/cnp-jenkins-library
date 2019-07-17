import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription

def call(String product, String environment, String subscription, Closure body) {
  call(product, environment, subscription, '', body)
}
def call(String product, String environment, String subscription, String deploymentTargets, Closure body) {

  Subscription metricsSubscription = new Subscription(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "", metricsSubscription )

  def pipelineConfig = new InfraPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new InfraPipelineDsl(callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  def deploymentTargetList = (deploymentTargets) ? deploymentTargets.split(',') as List : null

  node {
    try {
      env.PATH = "$env.PATH:/usr/local/bin"

      stage('Checkout') {
        callbacksRunner.callAround('checkout') {
          deleteDir()
          checkout scm
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
      if (pipelineConfig.slackChannel) {
        notifyBuildFailure channel: pipelineConfig.slackChannel
      }

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      deleteDir()
    }

    if (pipelineConfig.slackChannel) {
      notifyBuildFixed channel: pipelineConfig.slackChannel
    }

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
