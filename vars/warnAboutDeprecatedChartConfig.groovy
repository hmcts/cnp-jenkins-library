import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.contino.MetricsPublisher

import java.time.LocalDate

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component
  def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)

  writeFile file: 'check-helm-api-version.sh', text: libraryResource('uk/gov/hmcts/helm/check-helm-api-version.sh')
  writeFile file: 'check-deprecated-charts.sh', text: libraryResource('uk/gov/hmcts/helm/check-deprecated-charts.sh')

  try {
    sh """
    chmod +x check-helm-api-version.sh
    ./check-helm-api-version.sh $product $component
    """
  } finally {
    sh 'rm -f check-helm-api-version.sh'
  }

  try {
    sh """
    chmod +x check-deprecated-charts.sh
    ./check-deprecated-charts.sh $product $component
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_helmcharts", "Please upgrade base helm charts to latest. See releases on the chart repo for latest updates, example: https://github.com/hmcts/chart-java/releases", LocalDate.of(2023, 02, 14))
    notifyPipelineDeprecations(slackChannel, metricsPublisher)
  } finally {
    sh 'rm -f check-deprecated-charts.sh'
  }

}
