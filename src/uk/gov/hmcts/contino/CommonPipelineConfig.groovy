package uk.gov.hmcts.contino
import java.time.LocalDate

class CommonPipelineConfig implements Serializable {
  String slackChannel

  Set<String> branchesToSyncWithMaster = []

  nextMonth = LocalDate.now().plusDays(30)

  Set<String> expiresAfter = nextMonth.format("yyyy-MM-dd")

}