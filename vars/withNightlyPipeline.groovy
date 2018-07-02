import uk.gov.hmcts.contino.NightlyBuilder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.NightlyPipeline


def call(type,product,component,Closure body) {

  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, deploymentProduct, component),
    nodejs: new NodePipelineType(this, deploymentProduct, component),
    angular: new AngularPipelineType(this, deploymentProduct, component)
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

  NightlyBuilder builder = pipelineType.nBuilder

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
        if (pl.enablePerformanceTest) {
          sectionPerformanceTest(pl, builder)
        }
        if (pl.crossBrowserTest) {
          sectionCrossBrowserTest(pl, builder)
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

