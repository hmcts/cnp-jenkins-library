package uk.gov.hmcts.contino

import spock.lang.Specification

class BuildRecoveryTest extends Specification {
  Map identity = [product: 'apple', component: 'recipe-backend', branch_name: 'master',
    job_name: 'apple/recipe-backend/master', git_url: 'https://github.com/hmcts/recipe-backend',
    git_commit: 'abc123', shared_library_name: 'Infrastructure', shared_library_version: 'master']

  Map record(Map details) {
    return identity + [build_number: '35'] + details
  }

  List<Map> records() {
    return [
      record([event_type: 'step', current_step_name: 'test', step_result: 'STARTED', step_execution_id: 'test-1']),
      record([event_type: 'step', current_step_name: 'test', step_result: 'SUCCESS', step_execution_id: 'test-1']),
      record([event_type: 'step_artifacts', current_step_name: 'test', artifact_paths: BuildRecovery.TEST_ARTIFACTS]),
      record([event_type: 'step', current_step_name: 'securitychecks', step_result: 'STARTED', step_execution_id: 'security-1']),
      record([event_type: 'step', current_step_name: 'securitychecks', step_result: 'SUCCESS', step_execution_id: 'security-1']),
      record([event_type: 'step', current_step_name: 'dockerbuild', step_result: 'FAILURE', failure_reason: 'AGENT_DISCONNECTED']),
      record([event_type: 'pipeline', pipeline_result: 'FAILURE', failure_reason: 'AGENT_DISCONNECTED'])
    ]
  }

  def 'only successful allowlisted steps from the previous failed build are reusable'() {
    expect:
      BuildRecovery.reusableSteps(records(), identity, '35') == ['test', 'securitychecks'] as Set
  }

  def 'different source identity or build disables recovery'() {
    given:
      def events = records()
      events[0][field] = 'different'

    expect:
      BuildRecovery.reusableSteps(events, identity, '35').isEmpty()

    where:
      field << ['product', 'component', 'branch_name', 'job_name', 'git_url', 'git_commit', 'shared_library_name', 'shared_library_version', 'build_number']
  }

  def 'application failures prohibit recovery even alongside agent failures'() {
    given:
      def events = records()
      events << record([event_type: 'step', current_step_name: 'sonarscan', step_result: 'FAILURE', failure_reason: 'STEP_FAILURE'])

    expect:
      BuildRecovery.reusableSteps(events, identity, '35').isEmpty()
  }

  def 'missing terminal record or old metrics disable recovery'() {
    expect:
      BuildRecovery.reusableSteps(events, identity, '35').isEmpty()

    where:
      events << [[], [[current_step_name: 'test', current_build_current_result: 'FAILURE', node_status: 'offline']]]
  }

  def 'incomplete ambiguous or unarchived test results are rerun'() {
    given:
      def events = records()
      if (removeArtifacts) {
        events.removeAll { it.event_type == 'step_artifacts' }
      } else if (duplicate) {
        events << record([event_type: 'step', current_step_name: 'test', step_result: 'STARTED', step_execution_id: 'test-2'])
      } else {
        events.removeAll { it.current_step_name == 'test' && it.step_result == 'SUCCESS' }
      }

    expect:
      BuildRecovery.reusableSteps(events, identity, '35') == ['securitychecks'] as Set

    where:
      removeArtifacts | duplicate
      true            | false
      false           | true
      false           | false
  }

  def 'legacy retry attempt records are not reused'() {
    given:
      def events = records()
      events.findAll { it.current_step_name in ['test', 'securitychecks'] }.each { it.attempt_id = 'old-attempt' }

    expect:
      BuildRecovery.reusableSteps(events, identity, '35').isEmpty()
  }

  def 'plan restores test artifacts from the previous build'() {
    given:
      def steps = new Expando()
      def copied = []
      def reports = []
      steps.echo = { String message -> }
      steps.specific = { String build -> [build: build] }
      steps.copyArtifacts = { Map options -> copied << options }
      steps.junit = { Map options -> reports << options }
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      recovery.readMetrics(identity, '35') >> records()

    when:
      recovery.prepare(true)

    then:
      recovery.shouldSkip('test')
      recovery.shouldSkip('securitychecks')
      !recovery.shouldSkip('dockerbuild')
      copied[0].selector == [build: '35']
      copied[0].filter == BuildRecovery.TEST_ARTIFACTS
      reports == [[testResults: '**/test-results/test/*.xml', allowEmptyResults: false]]
  }

  def 'unavailable artifacts rerun tests while retaining eligible security checks'() {
    given:
      def steps = new Expando(echo: { String message -> }, specific: { String build -> build },
        copyArtifacts: { Map options -> throw new IllegalStateException('missing artifacts') })
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      recovery.readMetrics(identity, '35') >> records()

    when:
      recovery.prepare(true)

    then:
      !recovery.shouldSkip('test')
      recovery.shouldSkip('securitychecks')
  }

  def 'unavailable Cosmos falls back to normal execution'() {
    given:
      def steps = new Expando(echo: { String message -> })
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      recovery.readMetrics(identity, '35') >> { throw new IllegalStateException('unavailable') }

    when:
      recovery.prepare(true)

    then:
      !recovery.shouldSkip('test')
      !recovery.shouldSkip('securitychecks')
  }

  def 'disabled recovery and nonfailed previous builds do not query Cosmos'() {
    given:
      def publisher = Mock(MetricsPublisher)
      def recovery = Spy(BuildRecovery, constructorArgs: [new Expando(echo: { String message -> }), [previousBuild: previous], publisher, enabled])

    when:
      recovery.prepare(true)

    then:
      0 * recovery.readMetrics(_, _)
      0 * publisher.recoveryIdentity()
      !recovery.shouldSkip('test')

    where:
      enabled | previous
      false   | [number: 35, result: 'FAILURE']
      true    | null
      true    | [number: 35, result: 'SUCCESS']
      true    | [number: 35, result: 'ABORTED']
  }

  def 'missing identity disables querying and skipping'() {
    given:
      def sourceIdentity = new LinkedHashMap(identity)
      sourceIdentity.git_commit = null
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> sourceIdentity }
      def steps = new Expando(echo: { String message -> })
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])

    when:
      recovery.prepare(true)

    then:
      0 * recovery.readMetrics(_, _)
      !recovery.shouldSkip('test')
  }

  def 'non Gradle tests are never skipped'() {
    given:
      def steps = new Expando(echo: { String message -> })
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      recovery.readMetrics(identity, '35') >> records()

    when:
      recovery.prepare(false)

    then:
      !recovery.shouldSkip('test')
      recovery.shouldSkip('securitychecks')
  }

  def 'manual interruption is propagated rather than converted to fallback'() {
    given:
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [new Expando(echo: { String message -> }), [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      recovery.readMetrics(identity, '35') >> { throw new InterruptedException('aborted') }

    when:
      recovery.prepare(true)

    then:
      thrown(InterruptedException)
  }

  def 'successful pipeline and missing failure classification do not qualify'() {
    given:
      def events = records()
      events.removeAll { it.current_step_name == 'dockerbuild' }
      events.find { it.event_type == 'pipeline' }.putAll([pipeline_result: pipelineResult, failure_reason: reason])

    expect:
      BuildRecovery.reusableSteps(events, identity, '35').isEmpty()

    where:
      pipelineResult | reason
      'SUCCESS'      | 'AGENT_DISCONNECTED'
      'FAILURE'      | 'STEP_FAILURE'
      'FAILURE'      | null
  }

  def 'Cosmos check logs results without restoring artifacts and prepare reuses the query'() {
    given:
      List<String> messages = []
      List<Map> copies = []
      int queries = 0
      def steps = new Expando(
        echo: { String message -> messages << message },
        specific: { String build -> build },
        copyArtifacts: { Map options -> copies << options },
        junit: { Map options -> }
      )
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      recovery.readMetrics(identity, '35') >> { queries++; records() }

    when:
      recovery.checkPreviousBuild()

    then:
      queries == 1
      copies.isEmpty()
      messages.any { it.contains('querying Cosmos DB pipeline-metrics') && it.contains('build #35') }
      messages.any { it.contains('Cosmos DB returned 7 records') }
      !messages.any { it.contains('test=SUCCESS') }
      messages.any { it.contains('dockerbuild=FAILURE') && it.contains('AGENT_DISCONNECTED') }

    when:
      recovery.prepare(true)

    then:
      queries == 1
      copies.size() == 1
      recovery.shouldSkip('test')
      recovery.shouldSkip('securitychecks')
  }

  def 'only agent disconnections are displayed while all records determine recovery'() {
    given:
      List<String> messages = []
      def steps = new Expando(echo: { String message -> messages << message })
      def publisher = Mock(MetricsPublisher) { recoveryIdentity() >> identity }
      def recovery = Spy(BuildRecovery, constructorArgs: [steps, [previousBuild: [number: 35, result: 'FAILURE']], publisher, true])
      def events = records()
      events << record([event_type: 'step', current_step_name: 'sonarscan', step_result: 'FAILURE', failure_reason: 'STEP_FAILURE'])
      recovery.readMetrics(identity, '35') >> events

    when:
      recovery.checkPreviousBuild()

    then:
      def displayed = messages.findAll { it.startsWith('Previous build #35:') }
      displayed.size() == 1
      displayed[0].contains('dockerbuild=FAILURE')
      displayed[0].contains('failure_reason=AGENT_DISCONNECTED')
      !recovery.shouldSkip('test')
      !recovery.shouldSkip('securitychecks')
  }
}