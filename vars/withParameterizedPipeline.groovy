import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner

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

  Subscription metricsSubscription = new Subscription(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, metricsSubscription)
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

  def deploymentTargetList = deploymentTargets.split(',') as List

  try {
    node {
      env.PATH = "$env.PATH:/usr/local/bin"

      stage('Checkout') {
        callbacksRunner.callAround('checkout') {
          deleteDir()
          checkout scm
        }
      }

      stage("Build") {
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
        environment: environment,
        product: product,
        component: component,
        deploymentTargets: deploymentTargetList)
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
  }

  if (pipelineConfig.slackChannel) {
    notifyBuildFixed channel: pipelineConfig.slackChannel
  }

  callbacksRunner.call('onSuccess')
  node {
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
