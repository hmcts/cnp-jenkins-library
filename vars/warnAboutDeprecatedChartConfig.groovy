import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate
import uk.gov.hmcts.pipeline.DeprecationConfig

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component
  def helmChartDeprecationConfig = DeprecationConfig.deprecationConfig.helm

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

  sh 'chmod +x check-deprecated-charts.sh'

  helmChartDeprecationConfig.each { chart, deprecatedVersions ->
    deprecatedVersions.each { deprecatedVersion, deprecationDate ->
      try {
        sh """./check-deprecated-charts.sh $product $component $chart $deprecatedVersion """
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_helmcharts", "Please upgrade base helm charts to latest. See releases on the chart repo for latest updates, example: https://github.com/hmcts/chart-$chart/releases", LocalDate.parse(deprecationDate))
      }
    }
  }
  sh 'rm -f check-deprecated-charts.sh'

}
