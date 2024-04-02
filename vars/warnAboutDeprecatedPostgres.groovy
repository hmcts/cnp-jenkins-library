import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {
String gitUrl = env.GIT_URL;
LocalDate expiryDate;

switch (gitUrl.toLowerCase()) {
    case "https://github.com/hmcts/am-org-role-mapping-service.git":
    case "https://github.com/hmcts/am-role-assignment-service.git":
    case "https://github.com/hmcts/am-judicial-booking-service.git":
    case "https://github.com/hmcts/rd-commondata-api.git":
    case "https://github.com/hmcts/rd-professional-api.git":
    case "https://github.com/hmcts/rd-location-ref-api.git":
    case "https://github.com/hmcts/rd-judicial-api.git":
    case "https://github.com/hmcts/rd-caseworker-ref-api.git":
    case "https://github.com/hmcts/rd-user-profile-api.git":
    case "https://github.com/hmcts/rd-profile-sync.git":
        expiryDate = LocalDate.of(2024, 4, 29);
        break;
    case "https://github.com/hmcts/bulk-scan-processor.git":
    case "https://github.com/hmcts/bulk-scan-orchestrator.git":
    case "https://github.com/hmcts/bulk-scan-payment-processor.git":
    case "https://github.com/hmcts/blob-router-service.git":
    case "https://github.com/hmcts/reform-scan-notification-service.git":
    case "https://github.com/hmcts/send-letter-service.git":
        expiryDate = LocalDate.of(2024, 3, 31);
        break;
    case "https://github.com/hmcts/wa-case-event-handler.git":
        expiryDate = LocalDate.of(2024, 2, 12);
        break;
    case "https://github.com/hmcts/ecm-shared-infrastructure.git":
        expiryDate = LocalDate.of(2024, 4, 19);
        break;
    case "https://github.com/hmcts/ccd-data-store-api.git":
    case "https://github.com/hmcts/ccd-definition-store-api.git":
    case "https://github.com/hmcts/ccd-user-profile-api.git":
    case "https://github.com/hmcts/hmc-cft-hearing-service.git":
    case "https://github.com/hmcts/cpo-case-payment-orders-api.git":
        expiryDate = LocalDate.of(2024, 5, 22);
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
