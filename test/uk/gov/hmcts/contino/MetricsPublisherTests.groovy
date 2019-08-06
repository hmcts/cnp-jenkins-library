package uk.gov.hmcts.contino

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
                      COSMOSDB_TOKEN_KEY: "ABCDEFGHIJKLMNOPQRSTUVWXYZdIpG9oDdCvHL57pW52CzcCTKNLYV4xWjAhIRI7rScUfDAfA6oiPV7piAwdpw==",
                      PROD_SUBSCRIPTION_NAME: "prod"]

    def closure
    stubSteps.withCredentials(_, { closure = it }) >> { closure.call() }
    stubSteps.echo(_) >> { System.out.println(it) }

    }

  def "Executes without throwing uncaught errors"() {
    when:
    def subscription = new Subscription(stubSteps.env)
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', subscription.prodName)
    metricsPublisher.publish()

    then:
    notThrown()
  }

  def "collects build metrics"() {
    when:
    def subscription = new Subscription(stubSteps.env)
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', subscription.prodName)
    def metricsMap = metricsPublisher.collectMetrics('current stepName')

    then:
    assertThat(metricsMap).contains(entry("component", "testComponent"))
    assertThat(metricsMap).contains(entry("product", "testProduct"))
    assertThat(metricsMap).contains(entry("branch_name", "master"))
    assertThat(metricsMap).contains(entry("current_build_scheduled_time", "2017-12-18T16:15:48Z"))
  }
}
