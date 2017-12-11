package uk.gov.hmcts.contino

import spock.lang.Shared
import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class MetricsPublisherTests extends Specification {

  @Shared
    stubSteps

  def setup() {
    stubSteps = Stub(JenkinsStepMock.class)
    stubSteps.currentBuild >>  []
    stubSteps.env >> [BRANCH_NAME: "master",
                      COSMOSDB_TOKEN_TYPE: "master",
                      COSMOSDB_TOKEN_VERSION: "1.2",
                      COSMOSDB_TOKEN_KEY: "ABCDEFGHIJKLMNOPQRSTUVWXYZdIpG9oDdCvHL57pW52CzcCTKNLYV4xWjAhIRI7rScUfDAfA6oiPV7piAwdpw=="]
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

  def "creates Authorization header value containing token type and version"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def authToken = metricsPublisher.generateAuthToken(stubSteps.env.COSMOSDB_TOKEN_TYPE, stubSteps.env.COSMOSDB_TOKEN_VERSION, stubSteps.env.COSMOSDB_TOKEN_KEY).toString()

    then:
    assertThat(authToken).startsWith("type=master&ver=1.2&sig=")
  }

  def "creates Authorization header value containing expected token signature"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def authToken = metricsPublisher.generateAuthToken(stubSteps.env.COSMOSDB_TOKEN_TYPE, stubSteps.env.COSMOSDB_TOKEN_VERSION, stubSteps.env.COSMOSDB_TOKEN_KEY).toString()

    then:
    assertThat(authToken).contains("sig=Uq6Ylg1zIxj1QEq3D8Pblf6Ps5KrFsJ95aiZNK2VYA0=")
  }
}
