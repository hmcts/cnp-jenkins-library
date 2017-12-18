package uk.gov.hmcts.contino

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class MetricsPublisherTests extends Specification {

  @Shared
    stubSteps

  def setup() {
    stubSteps = Mock(JenkinsStepMock.class)
    stubSteps.currentBuild >>  ["timeInMillis" : 1513613748925]
    stubSteps.env >> [BRANCH_NAME: "master",
                      COSMOSDB_TOKEN_KEY: "ABCDEFGHIJKLMNOPQRSTUVWXYZdIpG9oDdCvHL57pW52CzcCTKNLYV4xWjAhIRI7rScUfDAfA6oiPV7piAwdpw=="]
    }

  @Ignore("Figure out how to make this work since pushing down the withCredentials into publish()")
  def "generates a http request setting JSON content type"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    metricsPublisher.publish()

    then:
    1 * stubSteps.httpRequest({
      it.containsKey("contentType")
      it.containsValue("APPLICATION_JSON")
    })
  }

  @Ignore("Figure out how to make this work since pushing down the withCredentials into publish()")
  def "generates a http request sending required headers"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    metricsPublisher.publish()

    then:
    1 * stubSteps.httpRequest({
      it.containsKey("customHeaders") &&
      it["customHeaders"].contains(entry("name", "x-ms-version"))
    })
  }

  def "collects build metrics"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def metricsMap = metricsPublisher.collectMetrics('current stepName')

    then:
    assertThat(metricsMap).contains(entry("branch_name", "master"))
    assertThat(metricsMap).contains(entry("current_build_scheduled_time", "2017-12-18T16:15:48Z"))
  }

  def "creates Authorization header value containing token type, version and signature"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild)
    def authToken = metricsPublisher.generateAuthToken('POST', 'resourceType', 'dateString', 'master', '1.0', stubSteps.env.COSMOSDB_TOKEN_KEY)

    then:
    assertThat(authToken.toString()).startsWith("type%3Dmaster%26ver%3D1.0%26sig%3DFUL70fm2xCq5y16AEdDwjEkqDLCV6%2But%2FJv4xMEPwp8%3D")
  }
}
