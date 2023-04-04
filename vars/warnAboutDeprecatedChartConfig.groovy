import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component

  def deprecationMap = [
    'java' : ['version':'4.0.13', deprecationDate: '29/03/2023'],
    'nodejs' : ['version':'2.4.14', deprecationDate: '29/03/2023'],
    'job' : ['version':'0.7.11', deprecationDate: '29/03/2023'],
    'blobstorage' : ['version':'0.3.0', deprecationDate: '29/03/2023'],
    'servicebus' : ['version':'0.4.0', deprecationDate: '29/03/2023'],
    'ccd' : ['version':'8.0.27', deprecationDate: '29/03/2023'],
    'elasticsearch' : ['version':'7.17.3', deprecationDate: '29/03/2023'],
  ]

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

  deprecationMap.each { chart, deprecatedVersions ->
    try {
      sh """./check-deprecated-charts.sh $product $component $chart $deprecatedVersions.version """
    } catch(ignored) {
      WarningCollector.addPipelineWarning("deprecated_helmcharts", "Please upgrade base helm charts to latest. See releases on the chart repo for latest updates, example: https://github.com/hmcts/chart-$chart/releases", LocalDate.parse(deprecatedVersions.deprecationDate))
    }
  }
  sh 'rm -f check-deprecated-charts.sh'

}
