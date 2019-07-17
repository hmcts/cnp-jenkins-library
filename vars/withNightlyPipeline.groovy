import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner

def call(type,product,component,Closure body) {


  def pipelineTypes = [
    nodejs : new NodePipelineType(this, product, component),
    java   : new SpringBootPipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component)
  ]

  PipelineType pipelineType = pipelineTypes.get(type)

  assert pipelineType != null

  Subscription subscription = new Subscription(env)

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, subscription)
  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register callbacks

  dsl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  try {
    node {
      env.PATH = "$env.PATH:/usr/local/bin"
      withSubscription(subscription.nonProdName) {
        sectionNightlyTests(callbacksRunner, pipelineConfig, pipelineType)
      }
      assert  pipelineType!= null
    }
  } catch (err) {
    currentBuild.result = "FAILURE"
    if (pipelineConfig.slackChannel) {
      notifyBuildFailure channel: pipelineConfig.slackChannel
    }

    callbacksRunner.call('onFailure')
    node {
      metricsPublisher.publish('Pipeline Failed')
    }
    throw err
  } finally {
    deleteDir()
  }

  if (pipelineConfig.slackChannel) {
    notifyBuildFixed channel: pipelineConfig.slackChannel
  }

  callbacksRunner.call('onSuccess')
  node {
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
