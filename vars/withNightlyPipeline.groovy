import uk.gov.hmcts.contino.NightlyBuilder
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.NightlyPipeline


def call(type,Closure body) {

  String typeOfTesting

  /*for(int i=0;i<type.length();i++){
    if (type[i].equalIgnorecase('crossBrowser')||('PerformanceTest')){
      np  : new NightlyPipeline(this)
    }
  }*/
  def pipelineTypes = [
    crossBrowser  : new NightlyPipeline(this),
    PerformanceTest : new NightlyPipeline(this)
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


    MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, "product", "component")
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
          SectionNightlyTest(pl, builder)
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

