import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {
  switch (env.GIT_URL) {
      case "https://github.com/HMCTS/am-org-role-mapping-service.git":
      case "https://github.com/hmcts/am-role-assignment-service.git":
      case "https://github.com/hmcts/am-judicial-booking-service.git":
        expirydate = LocalDate.of(2024, 03, 31)
        break
      case "https://github.com/hmcts/wa-case-event-handler.git":
        expirydate = LocalDate.of(2024, 02, 9)
        break
      default:
        expirydate = LocalDate.of(2024, 01, 31)
        break
    }


    https://github.com/hmcts/wa-case-event-handler

  writeFile file: 'check-deprecated-postgres.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres.sh')

  try {
    sh """
    chmod +x check-deprecated-postgres.sh
    ./check-deprecated-postgres.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_postgres", "Please migrate to the flexible server postgres module. See this Slack announcement for more info https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1692714862133249", expirydate)
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
