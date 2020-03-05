package uk.gov.hmcts.pipeline.deprecation

import org.apache.commons.lang3.time.DateUtils
import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class WarningCollectorTest extends Specification {

  Date nextDay = DateUtils.addDays(new Date(),1)
  Date now = new Date()
  Date nextWeek = DateUtils.addDays(new Date(),7)
  String nextWeekFormattedDate = nextWeek.format("dd/MM/yyyy HH:mm aa", TimeZone.getTimeZone("UTC"))
  String nextDayFormattedDate = nextDay.format("dd/MM/yyyy HH:mm aa", TimeZone.getTimeZone("UTC"))

  void setup() {

  }

  def "addPipelineWarning() with future date should add"() {

    when:
    WarningCollector.pipelineWarnings.clear()
    WarningCollector.addPipelineWarning("test_key","test deprecation", nextDay )

    then:
    assertThat(WarningCollector.pipelineWarnings.size()).isEqualTo(1)
  }

  def "addPipelineWarning() with past date should throw exception"() {

    when:
    WarningCollector.addPipelineWarning("test_key","test failure",new Date().parse("dd.MM.yyyy", "01.08.2019") )

    then:
    thrown RuntimeException
  }


  def "getMessageByDays() with same day should return today"() {

    String formattedDate = now.format("dd/MM/yyyy HH:mm aa", TimeZone.getTimeZone("UTC"))

    when:
    String message = WarningCollector.getMessageByDays(now)

    then:
    assertThat(message).isEqualTo(formattedDate+" ( today )")
  }

  def "getMessageByDays() with same day should return tomorrow"() {

    when:
    String message = WarningCollector.getMessageByDays(nextDay)

    then:
    assertThat(message).isEqualTo(nextDayFormattedDate+" ( tomorrow )")
  }

  def "getMessageByDays() with a week later should return in 7 days"() {

    when:
    String message = WarningCollector.getMessageByDays(nextWeek)

    then:
    assertThat(message).isEqualTo(nextWeekFormattedDate+" ( in 7 days )")
  }

  def "getSlackWarningMessage() should return expected message"() {

    when:
    WarningCollector.pipelineWarnings.clear()
    WarningCollector.addPipelineWarning("test_key","Test deprecation.", nextDay )
    WarningCollector.addPipelineWarning("test_key_2","Another test deprecation.", nextWeek )
    String message = WarningCollector.getSlackWarningMessage()

    String expectedMessage = "Test deprecation. This configuration will stop working by ${nextDayFormattedDate} ( tomorrow )\n\n" +
      "Another test deprecation. This configuration will stop working by ${nextWeekFormattedDate} ( in 7 days )\n\n"
    then:
    assertThat(message).isEqualTo(expectedMessage)
  }

}
