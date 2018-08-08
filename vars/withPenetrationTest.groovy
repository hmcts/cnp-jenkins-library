import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.MetricsPublisher

def call(Closure body) {

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, 'pen-test', 'pen-test')
  def pl = new PipelineCallbacks(metricsPublisher)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  timestamps {
    node {
      try {
        stage('clone') {
          deleteDir()
          checkout scm
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
        if (pl.slackChannel) {
          notifyBuildFailure channel: pl.slackChannel
        }

        pl.call('onFailure')
        metricsPublisher.publish('Pipeline Failed')
        throw err
      }

      if (pl.slackChannel) {
        notifyBuildFixed channel: pl.slackChannel
      }

      pl.call('onSuccess')
      metricsPublisher.publish('Pipeline Succeeded')
    }
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
