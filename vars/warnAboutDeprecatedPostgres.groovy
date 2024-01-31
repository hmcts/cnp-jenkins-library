import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)
  switch (${repositoryShortUrl}) {
      case "ccd":
        date = LocalDate.of(2024, 03, 31)
        break
      default:
        date = LocalDate.of(2024, 01, 31)
        break
    }

  writeFile file: 'check-deprecated-postgres.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres.sh')

  try {
    sh """
    chmod +x check-deprecated-postgres.sh
    ./check-deprecated-postgres.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_postgres", "${repositoryShortUrl} :Please migrate to the flexible server postgres module. See this Slack announcement for more info https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1692714862133249", LocalDate.of(2024, 01, 31))
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
