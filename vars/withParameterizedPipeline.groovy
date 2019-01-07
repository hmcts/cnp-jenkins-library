import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.Subscription

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
  def pl = new PipelineCallbacks(metricsPublisher, this)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  def deploymentTargetList = deploymentTargets.split(',') as List

  timestamps {
    try {
      node {
        env.PATH = "$env.PATH:/usr/local/bin"

        stage('Checkout') {
          pl.callAround('checkout') {
            deleteDir()
            checkout scm
          }
        }

        stage("Build") {
          pl.callAround('build') {
            builder.build()
          }
        }

        //TODO: remove
        echo "INFO: main file inside withParametrizedPiepline ${deploymentTargets}"
        
        sectionDeployToEnvironment(
          pipelineCallbacks: pl,
          pipelineType: pipelineType,
          subscription: subscription,
          environment: environment,
          product: product,
          component: component,
          deploymentTargets: deploymentTargetList)
      }
    } catch (err) {
      currentBuild.result = "FAILURE"
      if (pl.slackChannel) {
        notifyBuildFailure channel: pl.slackChannel
      }

      pl.call('onFailure')
      node {
        metricsPublisher.publish('Pipeline Failed')
      }
      throw err
    }

    if (pl.slackChannel) {
      notifyBuildFixed channel: pl.slackChannel
    }

    pl.call('onSuccess')
    node {
      metricsPublisher.publish('Pipeline Succeeded')
    }
  }
}
