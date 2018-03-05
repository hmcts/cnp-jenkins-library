import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.Environment

def call(type, String product, String component, Closure body) {
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

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  def pl = new PipelineCallbacks(metricsPublisher)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }
  currentBuild.result = "SUCCESS"

  Subscription subscription = new Subscription(env)
  Environment environment = new Environment(env)

  timestamps {
    try {
      node {
        env.PATH = "$env.PATH:/usr/local/bin"

        sectionBuildAndTest(pl, pipelineType.builder)

        onMaster {
          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscription.nonProdName,
            environment: environment.nonProdName,
            product: product,
            component: component)

          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscription.prodName,
            environment: environment.prodName,
            product: product,
            component: component)
          }

        onDemo {
          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscription.demoName,
            environment: environment.demoName,
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
