import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate
import uk.gov.hmcts.pipeline.DeprecationConfig

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component

  def helmChartDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().helm
  def gradleDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().gradle
  def npmDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().npm


  writeFile file: 'check-helm-api-version.sh', text: libraryResource('uk/gov/hmcts/helm/check-helm-api-version.sh')
  writeFile file: 'check-deprecated-charts.sh', text: libraryResource('uk/gov/hmcts/helm/check-deprecated-charts.sh')
  writeFile file: 'check-deprecated-gradle-dependency.sh', text: libraryResource('uk/gov/hmcts/gradle/check-deprecated-gradle-dependency.sh')
  writeFile file: 'check-deprecated-properties-volume-spring-boot-starter.sh', text: libraryResource('uk/gov/hmcts/gradle/check-deprecated-properties-volume-spring-boot-starter.sh')
  writeFile file: 'check-deprecated-npm-dependency.sh', text: libraryResource('uk/gov/hmcts/npm/check-deprecated-npm-dependency.sh')


  try {
    sh """
    chmod +x check-helm-api-version.sh
    ./check-helm-api-version.sh $product $component
    """
  } finally {
    sh 'rm -f check-helm-api-version.sh'
  }

  sh 'chmod +x check-deprecated-charts.sh'

  helmChartDeprecationConfig.each { chart, deprecation ->
    try {
      sh "./check-deprecated-charts.sh $product $component $chart $deprecation.version"
    } catch (ignored) {
      WarningCollector.addPipelineWarning("deprecated_helmcharts", "Version of $chart helm chart below $deprecation.version is deprecated, please upgrade to latest release https://github.com/hmcts/chart-$chart/releases", LocalDate.parse(deprecation.date_deadline))
    }
  }
  sh 'rm -f check-deprecated-charts.sh'

  sh 'chmod +x check-deprecated-gradle-dependency.sh'
  gradleDeprecationConfig.each { dependency, deprecation ->
    try {
      sh "./check-deprecated-gradle-dependency.sh $dependency $deprecation.version "
    } catch (ignored) {
      WarningCollector.addPipelineWarning("deprecated_gradle_library", "Versions below $dependency: $deprecation.version are deprecated, please upgrade to the latest release", LocalDate.parse(deprecation.date_deadline))
    }
  }
  sh 'rm -f check-deprecated-gradle-dependency.sh'
  
  sh 'chmod +x check-deprecated-properties-volume-spring-boot-starter.sh'
  try {
    sh './check-deprecated-properties-volume-spring-boot-starter.sh'
  } catch (ignored) {
    WarningCollector.addPipelineWarning("deprecated_gradle_library", "The properties-volume-spring-boot-starter is deprecated since spring-boot: 2.4.0, please follow steps to resolve.", LocalDate.of(2024, 6, 28))
  }
  sh 'rm -f check-deprecated-properties-volume-spring-boot-starter.sh'

  sh 'chmod +x check-deprecated-npm-dependency.sh'
  npmDeprecationConfig.each { dependency, deprecation ->
    try {
      sh "./check-deprecated-npm-dependency.sh $dependency $deprecation.version "
    } catch (ignored) {
      WarningCollector.addPipelineWarning("deprecated_npm_library", "Versions below $dependency: $deprecation.version are deprecated, please upgrade to the latest release", LocalDate.parse(deprecation.date_deadline))
    }
  }
  sh 'rm -f check-deprecated-npm-dependency.sh'
}
