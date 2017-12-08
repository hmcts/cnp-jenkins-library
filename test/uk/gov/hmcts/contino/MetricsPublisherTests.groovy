package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class MetricsPublisherTests extends Specification {

  def "generates a curl command sending a POST"() {
    when:
    def metricsPublisher = new MetricsPublisher([BRANCH_NAME:"master"], [])
    def commandString = metricsPublisher.generateCommandString()

    then:
    assertThat(commandString.toString()).startsWith("curl")
                                        .contains("XPOST")
  }

  def "generates a curl command setting JSON content type"() {
    when:
    def metricsPublisher = new MetricsPublisher([BRANCH_NAME:"master"], [])
    def commandString = metricsPublisher.generateCommandString()

    then:
    assertThat(commandString.toString()).startsWith("curl")
                                        .contains("Content-Type: application/json")
  }

  def "collects build metrics"() {
    when:
    def metricsPublisher = new MetricsPublisher([BRANCH_NAME:"master"], [])
    def metricsMap = metricsPublisher.collectMetrics()

    then:
    assertThat(metricsMap).contains(entry("branch_name", "master"))
  }

}
