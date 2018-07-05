import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.Environment


def call(type,product,component,Closure body) {


  def pipelineTypes = [
    nodejs : new NodePipelineType(this, product, component),
    jave   : new SpringBootPipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component)
  ]

  pipelineType = pipelineTypes.get(type)

  echo "pipeline type is ${pipelineType}"

  assert pipelineType != null

  Builder builder = pipelineType.builder

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  def pl = new PipelineCallbacks(metricsPublisher)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  Subscription subscription = new Subscription(env)
  Environment environment = new Environment(env)

  timestamps {
    try {
      node {
        env.PATH = "$env.PATH:/usr/local/bin"
        sectionNightlyTests()

//          sectionNightlyTests(
//            pipelineCallbacks: pl,
//            pipelineType: pipelineType,
//            subscription: subscription.nonProdName,
//            environment: environment.nonProdName,
//            product: deploymentProduct,
//            component: component)

      assert pl!= null
        assert  pipelineType!= null
      }
    }
    catch (err) {
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
