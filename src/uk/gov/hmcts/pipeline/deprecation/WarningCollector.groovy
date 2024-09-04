package uk.gov.hmcts.pipeline.deprecation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import uk.gov.hmcts.pipeline.SlackBlockMessage

class WarningCollector implements Serializable {

  static List<DeprecationWarning> pipelineWarnings = new ArrayList<>()
  static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
    .ofPattern("dd/MM/yyyy");
  static slackMessage = new SlackBlockMessage()

  static void addPipelineWarning(String warningKey, String warningMessage, LocalDate deprecationDate) {
    if (deprecationDate.isBefore(LocalDate.now())){
      throw new RuntimeException(warningMessage + " This change is enforced from ${deprecationDate.format(DATE_FORMATTER)} ")
    }
    pipelineWarnings.add(new DeprecationWarning(warningKey, warningMessage, deprecationDate))
  }

  static String getMessageByDays(LocalDate deprecationDate) {
    LocalDate currentDate = LocalDate.now()
    LocalDate nextDay = currentDate.plusDays(1)
    LocalDate nextYear = currentDate.plusYears(1)

    String message =  deprecationDate.format(DATE_FORMATTER)
    if (currentDate == deprecationDate) {
      return message.concat(" ( today )")
    } else if (nextDay == deprecationDate){
      return message.concat(" ( tomorrow )")
    } else if (nextYear == deprecationDate){
      return message
    }else{
      def daysBetween = ChronoUnit.DAYS.between(currentDate, deprecationDate)
      return message.concat(" ( in ${daysBetween} days )")
    }
  }

  /**
   * Constructs and returns a SlackBlockMessage object by adding all collected pipeline warnings as new sections.
   * Each warning includes a warning message and a deprecation date message.
   * Returns the complete SlackBlockMessage object will all pipeline warnings as sections.
   */
  static SlackBlockMessage getSlackWarningMessage() {
    for (pipelineWarning in pipelineWarnings) {
      slackMessage.addSection(pipelineWarning.warningMessage.concat(" This configuration will stop working by ").concat(getMessageByDays(pipelineWarning.deprecationDate)))
    }
    return slackMessage
  }
}
