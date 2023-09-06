import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {

  writeFile file: 'check-deprecated-postgres.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres.sh')

  try {
    sh """
    chmod +x check-deprecated-postgres.sh
    ./check-deprecated-postgres.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_postgres", "Please migrate to the flexible server postgres module https://github.com/hmcts/terraform-module-postgresql-flexible", LocalDate.of(2023, 11, 9))
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
