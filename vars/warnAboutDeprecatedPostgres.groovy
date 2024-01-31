import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.contino.RepositoryUrl

import java.time.LocalDate

def call() {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)
  switch (steps.env.PRODUCT) {
      case "ccd":
        def date = LocalDate.of(2024, 03, 31)
        break
      default:
        def date = LocalDate.of(2024, 01, 31)
        def test = steps.env.PRODUCT
        break
    }

  writeFile file: 'check-deprecated-postgres.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres.sh')

  try {
    sh """
    chmod +x check-deprecated-postgres.sh
    ./check-deprecated-postgres.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_postgres", "Please migrate to the flexible server postgres $test module. See this Slack announcement for more info https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1692714862133249", LocalDate.of(2024, 01, 31))
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
