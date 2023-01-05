package uk.gov.hmcts.contino
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CommonPipelineConfig implements Serializable {
  String slackChannel

  Set<String> branchesToSyncWithMaster = []

  LocalDate nextMonth = LocalDate.now().plusDays(30)

  Set<String> expiryDate = nextMonth.format("yyyy-MM-dd")

}