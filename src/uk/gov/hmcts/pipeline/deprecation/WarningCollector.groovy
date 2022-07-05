package uk.gov.hmcts.pipeline.deprecation

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class WarningCollector implements Serializable {

  static List<DeprecationWarning> pipelineWarnings = new ArrayList<>()

  static void addPipelineWarning(String warningKey, String warningMessage, LocalDate deprecationDate) {
    if (deprecationDate.isBefore(LocalDate.now())){
      throw new RuntimeException(warningMessage + " This change is enforced from ${deprecationDate.format("dd/MM/yyyy HH:mm")} ")
    }
    pipelineWarnings.add(new DeprecationWarning(warningKey, warningMessage, deprecationDate))
  }

  static String getMessageByDays(LocalDate deprecationDate) {
    String date = deprecationDate.format("dd/MM/yyyy")

    LocalDate currentDate = LocalDate.now()
    LocalDate nextDay = currentDate.plusDays(1)

    String message = "${date}"
    if (currentDate == deprecationDate) {
      return message.concat(" ( today )")
    } else if (nextDay == deprecationDate){
      return message.concat(" ( tomorrow )")
    }else{
      def daysBetween = ChronoUnit.DAYS.between(currentDate, deprecationDate)
      return message.concat(" ( in ${daysBetween} days )")
    }
  }

  static String getSlackWarningMessage() {
    String slackWarningMessage = ""
    for (pipelineWarning in pipelineWarnings) {
      slackWarningMessage = slackWarningMessage.concat(pipelineWarning.warningMessage).concat(" This configuration will stop working by ").concat(getMessageByDays(pipelineWarning.deprecationDate)).concat("\n\n")
    }
    return slackWarningMessage
  }

}
