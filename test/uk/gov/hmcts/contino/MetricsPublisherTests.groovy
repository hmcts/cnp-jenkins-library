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

    def closure
    stubSteps.withCredentials(_, { closure = it }) >> { closure.call() }
    }

  def "generates a http request setting JSON content type"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent')
    metricsPublisher.publish()

    then:
    1 * stubSteps.httpRequest({
      it.containsKey("contentType")
      it.containsValue("APPLICATION_JSON")
    })
  }

  def "generates a http request sending required headers"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent')
    metricsPublisher.publish()

    then:
    1 * stubSteps.httpRequest({
      it.containsKey("customHeaders")  &&
      it["customHeaders"]["name"].contains("Authorization") &&
      it["customHeaders"]["name"].contains("x-ms-version") &&
      it["customHeaders"]["name"].contains("x-ms-date")
    })
  }

  def "collects build metrics"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent')
    def metricsMap = metricsPublisher.collectMetrics('current stepName')

    then:
    assertThat(metricsMap).contains(entry("component", "testComponent"))
    assertThat(metricsMap).contains(entry("product", "testProduct"))
    assertThat(metricsMap).contains(entry("branch_name", "master"))
    assertThat(metricsMap).contains(entry("current_build_scheduled_time", "2017-12-18T16:15:48Z"))
  }

  def "creates Authorization header value containing token type, version and signature"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent')
    def authToken = metricsPublisher.generateAuthToken('POST', 'resourceType', 'dateString', 'master', '1.0', stubSteps.env.COSMOSDB_TOKEN_KEY)

    then:
    assertThat(authToken.toString()).startsWith("type%3Dmaster%26ver%3D1.0%26sig%3DE8C%2B5%2FWJCxBhvvXtPyvh%2Fy27UB3T%2F1Hm7zyBfRO8h5M%3D")
  }
}
