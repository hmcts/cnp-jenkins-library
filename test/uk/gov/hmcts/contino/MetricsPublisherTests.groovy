package uk.gov.hmcts.contino

import spock.lang.Shared
import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class MetricsPublisherTests extends Specification {

  @Shared
    stubSteps
  @Shared
    cosmosDbTargetResolver

  def setup() {
    stubSteps = Mock(JenkinsStepMock.class)
    stubSteps.currentBuild >>  ["timeInMillis" : 1513613748925]
    stubSteps.env >> [BRANCH_NAME: "master"]
    stubSteps.azureCosmosDBCreateDocument(_) >> {}

    stubSteps.echo(_) >> { System.out.println(it) }
    cosmosDbTargetResolver = Mock(CosmosDbTargetResolver) {
      databaseName() >> "jenkins"
    }

    }

  def "Executes without throwing uncaught errors"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', cosmosDbTargetResolver)
    metricsPublisher.publish()

    then:
    notThrown()
  }

  def "collects build metrics"() {
    when:
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', cosmosDbTargetResolver)
    def metricsMap = metricsPublisher.collectMetrics('current stepName')

    then:
    assertThat(metricsMap).contains(entry("component", "testComponent"))
    assertThat(metricsMap).contains(entry("product", "testProduct"))
    assertThat(metricsMap).contains(entry("branch_name", "master"))
    assertThat(metricsMap).contains(entry("current_build_scheduled_time", "2017-12-18T16:15:48Z"))
  }

  def "publishes to database returned by resolver"() {
    given:
    cosmosDbTargetResolver.databaseName() >> "sds-jenkins"
    def metricsPublisher = new MetricsPublisher(stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', cosmosDbTargetResolver)

    when:
    metricsPublisher.publish("some-step")

    then:
    1 * stubSteps.azureCosmosDBCreateDocument(_ as LinkedHashMap)
  }
}
