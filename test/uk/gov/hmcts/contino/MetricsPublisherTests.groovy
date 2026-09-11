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
    stubSteps.env >> [BRANCH_NAME: "master", SHARED_LIBRARY_VERSION: "feature/test-lib-branch", NODE_NAME: "agent-1"]
   
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
    assertThat(metricsMap).contains(entry("shared_library_name", "Infrastructure"))
    assertThat(metricsMap).contains(entry("shared_library_version", "feature/test-lib-branch"))
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

  def "adds stage duration and VM cost metrics"() {
    given:
    def pricing = Mock(AzureVmPricing)
    pricing.lookup('agent-1') >> [
      vm_sku           : 'Standard_D2s_v5',
      vm_region        : 'uksouth',
      vm_hourly_price  : new BigDecimal('0.111'),
      vm_price_currency: 'USD'
    ]
    def metricsPublisher = new MetricsPublisher(
      stubSteps,
      stubSteps.currentBuild,
      'testProduct',
      'testComponent',
      cosmosDbTargetResolver,
      pricing
    )

    when:
    def metrics = metricsPublisher.collectMetrics('build', 3_600_000L)

    then:
    metrics.stage_duration_ms == 3_600_000L
    metrics.vm_sku == 'Standard_D2s_v5'
    metrics.vm_region == 'uksouth'
    metrics.vm_hourly_price == new BigDecimal('0.111')
    metrics.vm_price_currency == 'USD'
    (metrics.stage_cost as BigDecimal).compareTo(new BigDecimal('0.111')) == 0
  }

  def "publishes null cost fields for build-level events without a duration"() {
    given:
    def pricing = Mock(AzureVmPricing)
    def metricsPublisher = new MetricsPublisher(
      stubSteps,
      stubSteps.currentBuild,
      'testProduct',
      'testComponent',
      cosmosDbTargetResolver,
      pricing
    )

    when:
    def metrics = metricsPublisher.collectMetrics('Pipeline Succeeded')

    then:
    metrics.stage_duration_ms == null
    metrics.vm_sku == null
    metrics.vm_region == null
    metrics.vm_hourly_price == null
    metrics.vm_price_currency == null
    metrics.stage_cost == null
    0 * pricing.lookup(_)
  }

  def "publishes metrics with null cost fields when pricing enrichment fails"() {
    given:
    def pricing = Mock(AzureVmPricing)
    pricing.lookup('agent-1') >> {
      throw new AzureVmPricingException('IMDS unavailable')
    }
    def metricsPublisher = new MetricsPublisher(
      stubSteps,
      stubSteps.currentBuild,
      'testProduct',
      'testComponent',
      cosmosDbTargetResolver,
      pricing
    )

    when:
    def metrics = metricsPublisher.collectMetrics('build', 1_000L)

    then:
    metrics.stage_duration_ms == 1_000L
    metrics.vm_sku == null
    metrics.stage_cost == null
    1 * stubSteps.echo({ it.contains('Unable to calculate stage cost') })
  }
}
