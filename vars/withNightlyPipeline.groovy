import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.AngularPipelineType

def call(type,product,component,Closure body) {


  def pipelineTypes = [
    nodejs : new NodePipelineType(this, product, component),
    java   : new SpringBootPipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component)
  ]

  PipelineType pipelineType = pipelineTypes.get(type)

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  def pl = new PipelineCallbacks(metricsPublisher)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  timestamps {
    try {
      node {
        env.PATH = "$env.PATH:/usr/local/bin"
        sectionNightlyTests(pl, pipelineType)
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
