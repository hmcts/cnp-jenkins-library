import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component

  writeFile file: 'warn-about-jitpack.sh', text: libraryResource('uk/gov/hmcts/pipeline/warn-about-jitpack.sh')

  try {
    sh "./warn-about-jitpack.sh"
  } catch (ignored) {
    WarningCollector.addPipelineWarning("deprecated_jitpack", "Jitpack is no longer supported on the project. Please update to use Azure DevOps Artifacts following the guide at https://hmcts.github.io/cloud-native-platform/common-pipeline/publishing-libraries/java.html", LocalDate.of(2025, 6, 30))
  }
  sh 'rm -f warn-about-jitpack.sh'
}
