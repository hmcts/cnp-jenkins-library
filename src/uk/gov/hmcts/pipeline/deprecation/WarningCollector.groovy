package uk.gov.hmcts.pipeline.deprecation

import org.apache.commons.lang.time.DateUtils

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit;

class WarningCollector implements Serializable {

  private static final TimeZone TIMEZONE = TimeZone.getTimeZone("Europe/London")
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/London") ;

  static List<DeprecationWarning> pipelineWarnings = new ArrayList<>()

  static void addPipelineWarning(String warningKey, String warningMessage, Date deprecationDate) {
    if (deprecationDate.before(new Date())){
      throw new RuntimeException(warningMessage + "This change is enforced from ${deprecationDate.format("dd/MM/yyyy HH:mm", TIMEZONE)} ")
    }
    pipelineWarnings.add(new DeprecationWarning(warningKey, warningMessage, deprecationDate))
  }

  static String getMessageByDays(Date deprecationDate) {
    String date = deprecationDate.format("dd/MM/yyyy HH:mm aa", TIMEZONE)

    ZonedDateTime now = ZonedDateTime.now(ZONE_ID) ;

    LocalDate deprecationLocalDate = deprecationDate.toInstant()
      .atZone(ZONE_ID)
      .toLocalDate();

    Date currentDate = new Date();
    Date nextDay = DateUtils.addDays(currentDate, 1);

    String message = "${date}"
    if (DateUtils.isSameDay(currentDate, deprecationDate)) {
      return message.concat(" ( today )")
    } else if (DateUtils.isSameDay(nextDay, deprecationDate)) {
      return message.concat(" ( tomorrow )")
    } else {
      def daysBetween = ChronoUnit.DAYS.between(
        now.toLocalDate(),
        deprecationLocalDate
      )
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
