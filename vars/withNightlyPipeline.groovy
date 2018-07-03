import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.NodePipelineType


def call(type,product,component,Closure body) {

  def pipelineTypes = [
    PerformanceTest: new NodePipelineType(this, product, component),
    crossBrowserTest: new NodePipelineType(this, product, component)
  ]
  PipelineType pipelineType

  type.each {
    if (it instanceof PipelineType) {
      if (it != null)
        pipelineType = it
    } else {
      pipelineType = pipelineTypes.get(it)
    }
  }
  assert pipelineType != null

  Builder builder = pipelineType.builder

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild,product,component)
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

        stage('Checkout') {
          pl.callAround('checkout') {
            deleteDir()
            checkout scm
          }
        }

        if (pl.crossBrowserTest) {
          try {
            sectionCrossBrowserTest(pl, builder)
          }
          catch (err) {
            currentBuild.result = "UNSTABLE"
          }
        }
        if (pl.performanceTest) {
          sectionPerformanceTest(pl,builder)
        }
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
