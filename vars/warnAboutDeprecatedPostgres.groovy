import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.contino.RepositoryUrl

import java.time.LocalDate

def call() {
  switch (env.GIT_URL) {
      case "https://github.com/HMCTS/am-org-role-mapping-service.git":
        newdate = LocalDate.of(2024, 03, 31)
        break
      default:
        newdate = LocalDate.of(2024, 01, 31)
        break
    }

  writeFile file: 'check-deprecated-postgres.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres.sh')

  try {
    sh """
    chmod +x check-deprecated-postgres.sh
    ./check-deprecated-postgres.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_postgres", "${env.GIT_URL} :Please migrate to the flexible server postgres module. See this Slack announcement for more info https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1692714862133249", newdate)
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
