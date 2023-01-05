package uk.gov.hmcts.contino
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CommonPipelineConfig implements Serializable {
  String slackChannel

  Set<String> branchesToSyncWithMaster = []

  def expiryDate = LocalDate.now().plusDays(30).toString()

}