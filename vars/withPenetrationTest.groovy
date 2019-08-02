import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription

def call(Closure body) {

  Subscription subscription = new Subscription(env)

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, 'pen-test', 'pen-test', subscription)
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

  node {
    try {
      stage('Checkout') {
        checkoutScm()
      }
      stage('Penetration Test - Kali Image') {
        withDocker('hmcts/kali-image:1.1', null) {
          withSubscription("${params.SUBSCRIPTION}") {
            env.AZURE_CONFIG_DIR = "/opt/jenkins/.azure-${params.SUBSCRIPTION}"
            def command = getCommand(params)
            sh "${command}"

            sh "python ITHCReport.py > ./reports/ITHCReport.txt"
          }
        }
      }
      stage('Upload Reports') {
        def buildNo = currentBuild.number
        azureBlobUpload('buildlog-storage-account', "${WORKSPACE}/reports", "infra-probe/${params.SUBSCRIPTION}/${buildNo}")
      }
    } catch (err) {
      currentBuild.result = "FAILURE"
      if (pipelineConfig.slackChannel) {
        notifyBuildFailure channel: pipelineConfig.slackChannel
      }

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      deleteDir()
    }

    if (pipelineConfig.slackChannel) {
      notifyBuildFixed channel: pipelineConfig.slackChannel
    }

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}

/**
 * This pipeline is designed to be run as a parameterised build.
 */
def getCommand(params) {
  def command = 'python InfraProbe.py '
  if (params.TEST_MODE == 'true') {
    command = command + '-t '
  }
  return command
}
