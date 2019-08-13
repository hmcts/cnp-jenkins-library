package uk.gov.hmcts.pipeline.deprecation

import org.apache.commons.lang.time.DateUtils
import org.joda.time.Days
import org.joda.time.DateTime;

class WarningCollector implements Serializable {

  static List<DeprecationWarning> pipelineWarnings = new ArrayList<>()

  static void addPipelineWarning(String warningKey, String warningMessage, Date deprecationDate) {
    if(deprecationDate.before(new Date())){
      throw new RuntimeException(warningMessage + "This change is enforced from ${deprecationDate.format("dd/MM/yyyy HH:mm", TimeZone.getTimeZone("UTC"))} ")
    }
    pipelineWarnings.add(new DeprecationWarning(warningKey, warningMessage, deprecationDate))
  }

  static String getMessageByDays(Date deprecationDate) {
    String date = deprecationDate.format("dd/MM/yyyy HH:mm aa", TimeZone.getTimeZone("UTC"))

    Date currentDate = new Date();
    Date nextDay = DateUtils.addDays(currentDate,1);

    String message = "${date}"
    if(DateUtils.isSameDay(currentDate, deprecationDate)){
      return message.concat(" ( today )")
    }else if (DateUtils.isSameDay(nextDay, deprecationDate)){
      return message.concat(" ( tomorrow )")
    }else{
      def daysBetween = Days.daysBetween(new DateTime(), new DateTime(deprecationDate)).getDays()
      return message.concat(" ( in ${daysBetween} days )")
    }
  }

  static String getSlackWarningMessage() {
    String slackWarningMessage = ""
    for(pipelineWarning in pipelineWarnings) {
      slackWarningMessage = slackWarningMessage.concat(pipelineWarning.warningMessage).concat(" This configuration will stop working by ").concat(getMessageByDays(pipelineWarning.deprecationDate)).concat("\n\n")
    }
    return slackWarningMessage
  }

}
