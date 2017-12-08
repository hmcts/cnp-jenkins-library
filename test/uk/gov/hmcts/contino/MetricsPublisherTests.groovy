package uk.gov.hmcts.contino

import spock.lang.Shared
import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class MetricsPublisherTests extends Specification {

  @Shared
    stubSteps

  def setup() {
    stubSteps = Stub(JenkinsStepMock.class)
    stubSteps.env >> [BRANCH_NAME: "master"]
    stubSteps.currentBuild >>  []
    }

  def "generates a curl command sending a POST"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def commandString = metricsPublisher.generateCommandString()

    then:
    assertThat(commandString.toString()).startsWith("curl")
                                        .contains("XPOST")
  }

  def "generates a curl command setting JSON content type"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def commandString = metricsPublisher.generateCommandString()

    then:
    assertThat(commandString.toString()).startsWith("curl")
                                        .contains("Content-Type: application/json")
  }

  def "collects build metrics"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def metricsMap = metricsPublisher.collectMetrics()

    then:
    assertThat(metricsMap).contains(entry("branch_name", "master"))
  }

}
