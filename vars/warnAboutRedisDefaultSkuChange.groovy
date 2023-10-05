
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {

  writeFile file: 'check-redis-sku.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-redis-sku.sh')

  try {
    sh """
    chmod +x check-redis-sku.sh
    ./check-redis-sku.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("Default Redis Sku has Changed to Basic", "Please follow instruction in https://github.com/hmcts/cnp-module-redis/blob/mokainos-patch-1/README.md ", LocalDate.of(2023, 11, 11))
  } finally {
    sh 'rm -f check-redis-sku.sh'
  }
}