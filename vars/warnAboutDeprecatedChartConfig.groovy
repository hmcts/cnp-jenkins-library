import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component

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
    WarningCollector.addPipelineWarning("deprecated_helmcharts", "Please upgrade base helm charts to latest. See https://github.com/hmcts/chart-java/releases, https://github.com/hmcts/chart-nodejs/releases, https://github.com/hmcts/chart-job/releases, https://github.com/hmcts/chart-blobstorage/releases, https://github.com/hmcts/chart-servicebus/releases - For Java upgrades, you may encounter postgres issues in your preview (PR) pods, which a commit like https://github.com/hmcts/send-letter-service/pull/1633/commits/51b8f2367c5779951d17bafa9ae1705fd3c90a7e will fix ", LocalDate.of(2022, 7, 21))
  } finally {
    sh 'rm -f check-deprecated-charts.sh'
  }

}
