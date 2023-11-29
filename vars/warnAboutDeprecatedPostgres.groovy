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
    WarningCollector.addPipelineWarning("deprecated_postgres", "Please migrate to the flexible server postgres module. See this Slack announcement for more info https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1692714862133249", LocalDate.of(2023, 11, 30))
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
