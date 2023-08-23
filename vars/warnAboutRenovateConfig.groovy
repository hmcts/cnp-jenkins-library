import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {

  writeFile file: 'renovate-config-check.sh', text: libraryResource('uk/gov/hmcts/renovate/renovate-config-check.sh')

  try {
    sh """
    chmod +x renovate-config-check.sh
    ./renovate-config-check.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("renovate_config", "Please note Renovate config is now mandatory in every repo extending default org level config without using enabledManagers ", LocalDate.of(2023, 9, 23))
  } finally {
    sh 'rm -f renovate-config-check.sh'
  }
}
