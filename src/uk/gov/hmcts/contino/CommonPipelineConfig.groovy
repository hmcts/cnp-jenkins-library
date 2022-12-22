package uk.gov.hmcts.contino
import java.time.LocalDate

class CommonPipelineConfig implements Serializable {
  String slackChannel

  Set<String> branchesToSyncWithMaster = []

  String expiresAfter ?: LocalDate.now().plusDays(30)

}