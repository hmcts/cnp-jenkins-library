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
    stubSteps.env >> [BRANCH_NAME: "master", SHARED_LIBRARY_VERSION: "feature/test-lib-branch"]
   
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
    noExceptionThrown()
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
    assertThat(metricsMap).contains(entry("node_status", "offline"))
  }

  def "records whether the build agent is online and connected"() {
    given:
    stubSteps.env.NODE_NAME = 'build-agent'
    def metricsPublisher = Spy(MetricsPublisher, constructorArgs: [stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', cosmosDbTargetResolver])
    metricsPublisher.agentComputer('build-agent') >> (agentExists ? [
      isOnline: { online },
      getChannel: { connected ? new Object() : null }
    ] : null)

    when:
    def metricsMap = metricsPublisher.collectMetrics('test')

    then:
    metricsMap.node_name == 'build-agent'
    metricsMap.node_status == expectedStatus

    where:
    agentExists | online | connected | expectedStatus
    true        | true   | true      | 'online'
    true        | false  | true      | 'offline'
    true        | true   | false     | 'offline'
    true        | false  | false     | 'offline'
    false       | false  | false     | 'offline'
  }

  def "agent lookup failure does not prevent collecting metrics"() {
    given:
    stubSteps.env.NODE_NAME = 'build-agent'
    def metricsPublisher = Spy(MetricsPublisher, constructorArgs: [stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', cosmosDbTargetResolver])
    metricsPublisher.agentComputer('build-agent') >> { throw new IllegalStateException('Jenkins is unavailable') }

    when:
    def metricsMap = metricsPublisher.collectMetrics('test')

    then:
    metricsMap.node_status == 'offline'
    metricsMap.product == 'testProduct'
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

  def 'publishes explicit outcomes and confirmed agent failure reasons'() {
    given:
      stubSteps.env.NODE_NAME = 'build-agent'
      stubSteps.currentBuild.result = 'FAILURE'
      def publisher = Spy(MetricsPublisher, constructorArgs: [stubSteps, stubSteps.currentBuild, 'testProduct', 'testComponent', cosmosDbTargetResolver])
      publisher.agentComputer('build-agent') >> {
        if (lookupFails) {
          throw new IllegalStateException('lookup denied')
        }
        return removed ? null : [isOnline: { online }, getChannel: { connected ? new Object() : null }]
      }
      Map document

    when:
      publisher.publishStep('test', result, 'execution-1', fails ?
        (communicationError ? new hudson.AbortException('script returned exit code -2') : new IllegalStateException('step failed')) : null)

    then:
      1 * stubSteps.azureCosmosDBCreateDocument(_ as LinkedHashMap) >> { Map options -> document = options.document }
      document.step_result == result
      document.step_execution_id == 'execution-1'
      document.event_type == 'step'
      !document.containsKey('attempt_id')
      document.current_build_result == 'FAILURE'
      document.failure_reason == expectedReason

    where:
      result    | fails | online | connected | removed | lookupFails | communicationError | expectedReason
      'SUCCESS' | false | true   | true      | false   | false       | false              | null
      'FAILURE' | true  | true   | true      | false   | false       | false              | 'STEP_FAILURE'
      'FAILURE' | true  | false  | false     | false   | false       | false              | 'STEP_FAILURE'
      'FAILURE' | true  | false  | false     | false   | false       | true               | 'AGENT_DISCONNECTED'
      'FAILURE' | true  | true   | false     | false   | false       | true               | 'AGENT_DISCONNECTED'
      'FAILURE' | true  | false  | false     | true    | false       | true               | 'AGENT_DISCONNECTED'
      'FAILURE' | true  | false  | false     | false   | true        | true               | 'STEP_FAILURE'
      'FAILURE' | true  | true   | true      | false   | false       | true               | 'STEP_FAILURE'
  }

}
