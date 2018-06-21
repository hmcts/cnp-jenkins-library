import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Gatling

def call(String product, String component, String environment, Closure body) {

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
        stage('Checkout') {
          pl.callAround('checkout') {
            deleteDir()
            checkout scm
          }
        }
        stage('Performance Test') {

          echo "TEST_URL is ${TEST_URL}"

          pl.callAround('performancetest') {
            def gatling = new Gatling(this)
            gatling.execute()

            def params = [environment: environment,
                          product    : product,
                          component  : component]

            publishPerformanceReports(this, params)
          }
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
