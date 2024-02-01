import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {
String gitUrl = env.GIT_URL;
LocalDate expiryDate;
 
switch (gitUrl.toLowercase()) {
    case "https://github.com/hmcts/am-org-role-mapping-service.git":
    case "https://github.com/hmcts/am-role-assignment-service.git":
    case "https://github.com/hmcts/am-judicial-booking-service.git":
        expiryDate = LocalDate.of(2024, 3, 31);
        break;
    case "https://github.com/hmcts/wa-case-event-handler.git":
        expiryDate = LocalDate.of(2024, 2, 12);
        break;
    default:
        expiryDate = LocalDate.of(2024, 1, 31);
        break;
}

  writeFile file: 'check-deprecated-postgres.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres.sh')

  try {
    sh """
    chmod +x check-deprecated-postgres.sh
    ./check-deprecated-postgres.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("deprecated_postgres", "Please migrate to the flexible server postgres module. See this Slack announcement for more info https://hmcts-reform.slack.com/archives/CA4F2MAFR/p1692714862133249", expiryDate)
  } finally {
    sh 'rm -f check-deprecated-postgres.sh'
  }
}
