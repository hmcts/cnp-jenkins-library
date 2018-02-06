import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.MetricsPublisher

def call(type, String product, String component, Closure body) {
  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, component),
    nodejs: new NodePipelineType(this, product, component)
  ]

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  def pl = new PipelineCallbacks(metricsPublisher)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }
  currentBuild.result = "SUCCESS"

  timestamps {
    try {
      node {
        env.PATH = "$env.PATH:/usr/local/bin"

        sectionBuildAndTest(pl, pipelineType.builder)

        onMaster {
          def subscription = 'nonprod'
          if (env.NONPROD_SUBSCRIPTION) {
            subscription = env.NONPROD_SUBSCRIPTION
          }
          def environment = 'aat'
          if (env.NONPROD_ENVIRONMENT) {
            environment = env.NONPROD_ENVIRONMENT
          }

          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            deployer: pipelineType.deployer,
            subscription: subscription,
            environment:environment,
            product: product,
            component: component)

          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            deployer: pipelineType.deployer,
            subscription:'prod',
            environment:'prod',
            product: product,
            component: component)
          }
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
