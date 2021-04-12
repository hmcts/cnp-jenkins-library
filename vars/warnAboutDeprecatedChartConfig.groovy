import uk.gov.hmcts.pipeline.deprecation.WarningCollector

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
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_helmapiversion", "Please upgrade helm chart api version to v2. For examples see: https://github.com/hmcts/service-auth-provider-app/pull/255 and https://github.com/hmcts/draft-store/pull/611/files", new Date().parse("dd.MM.yyyy", "22.02.2021"))
  } finally {
    sh 'rm -f check-helm-api-version.sh'
  }

  try {
    sh """
    chmod +x check-deprecated-charts.sh
    ./check-deprecated-charts.sh $product $component
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_helmcharts", "Please upgrade base helm charts to latest. See https://github.com/hmcts/chart-java/releases, https://github.com/hmcts/chart-nodejs/releases, https://github.com/hmcts/chart-job/releases ", new Date().parse("dd.MM.yyyy", "26.04.2021"))
  } finally {
    sh 'rm -f check-deprecated-charts.sh'
  }

}
