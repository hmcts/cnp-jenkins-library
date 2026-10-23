package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import spock.lang.Specification
import spock.lang.Unroll

class YarnAuditFinalizationTest extends Specification {
  File workspace
  def steps = Mock(JenkinsStepMock)
  RuntimeException auditFailure
  RuntimeException reportingFailure
  RuntimeException setupFailure
  String failureStage
  String currentReport
  String currentKnownIssues
  int wrapperCalls
  YarnBuilder builder

  def setup() {
    workspace = File.createTempDir('yarn-audit-finalizer-', '')
    steps.getEnv() >> [BRANCH_NAME: 'test']
    steps.usernamePassword(_) >> [:]
    steps.withCredentials(_, _) >> { credentials, body ->
      if (failureStage == 'credentials') { throw setupFailure }
      body()
    }
    steps.libraryResource(_) >> { String path ->
      if (failureStage == 'resource') { throw setupFailure }
      new File('resources', path).text
    }
    steps.writeFile(_) >> { Map args -> new File(workspace, args.file).text = args.text }
    steps.fileExists(_) >> { String path -> new File(workspace, path).exists() }
    steps.readFile(_) >> { String path ->
      if (reportingFailure) { throw reportingFailure }
      new File(workspace, path).text
    }
    steps.sh(_) >> { args ->
      if (args[0] instanceof Map) { return 0 }
      String script = args[0]
      if (script.contains('nvm install') && failureStage == 'nvm') { throw setupFailure }
      if (script.startsWith('rm -f') && failureStage == 'cleanup') { throw setupFailure }
      if (script.contains('./yarn-audit-with-suppressions.sh')) {
        wrapperCalls++
        if (currentReport != null) { new File(workspace, 'yarn-audit-result-formatted').text = currentReport }
        if (currentKnownIssues != null) { new File(workspace, 'yarn-audit-known-issues-result').text = currentKnownIssues }
        if (auditFailure) { throw auditFailure }
      }
      if (script.contains('yarn-audit-result-formatted')) {
        def process = new ProcessBuilder('bash', '-e', '-c', script).directory(workspace).start()
        def stdout = new StringBuffer()
        def stderr = new StringBuffer()
        process.consumeProcessOutput(stdout, stderr)
        process.waitForOrKill(10_000)
        if (process.exitValue() != 0) { throw new IllegalStateException(stderr.toString()) }
      }
    }
    builder = new YarnBuilder(steps)
  }

  def cleanup() {
    workspace.deleteDir()
  }

  @Unroll
  def 'unavailable report #name preserves the audit error without publishing'() {
    given:
    currentReport = report
    auditFailure = new IllegalStateException('original audit failure')

    when:
    builder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    failure.is(auditFailure)
    0 * steps.azureCosmosDBCreateDocument(_)

    where:
    name             | report
    'missing'        | null
    'empty'          | ''
    'malformed'      | 'Error: advisory request failed'
    'missing fields' | '{}'
    'null'           | 'null'
    'array'          | '[]'
    'bad advisories' | '{"metadata":{},"advisories":[]}'
    'bad entry'      | '{"metadata":{},"advisories":{"1":null}}'
    'bad metadata'   | '{"metadata":null,"advisories":{}}'
    'multiple JSON'  | '{"metadata":{},"advisories":{}}\n{}'
    'missing counts' | '{"metadata":{},"advisories":{}}'
  }

  def 'an unavailable report fails even when the audit command returned successfully'() {
    when:
    builder.securityCheck()

    then:
    thrown(IllegalStateException)
    0 * steps.azureCosmosDBCreateDocument(_)
  }

  @Unroll
  def 'valid report publishes when vulnerabilities=#vulnerabilities auditFailed=#auditFailed suppressed=#suppressed'() {
    given:
    def expected = writeReport(vulnerabilities)
    if (auditFailed) { auditFailure = new IllegalStateException('unsuppressed vulnerabilities') }
    if (suppressed) {
      currentKnownIssues = fixture('yarn-audit-report-suppressed.txt')
      expected = builder.prepareCVEReport(fixture('yarn-audit-report.txt'), fixture('yarn-audit-report-suppressed.txt'))
    }

    when:
    RuntimeException caught
    try { builder.securityCheck() } catch (RuntimeException failure) { caught = failure }

    then:
    caught.is(auditFailure)
    1 * steps.azureCosmosDBCreateDocument({ it.document.report == expected })

    where:
    vulnerabilities | auditFailed | suppressed
    false           | false       | false
    true            | false       | true
    true            | true        | false
  }

  private Map writeReport(boolean vulnerabilities) {
    String lines = fixture(vulnerabilities ? 'yarn-audit-report.txt' : 'yarn-audit-report-no-issues.txt')
    def entries = lines.readLines().findAll { it }.collect { new JsonSlurperClassic().parseText(it) }
    def report = [metadata: entries.find { it.type == 'auditSummary' }.data,
                  advisories: entries.findAll { it.type == 'auditAdvisory' }.collectEntries {
                    [(it.data.advisory.id.toString()): it.data.advisory]
                  }]
    currentReport = JsonOutput.toJson(report)
    builder.prepareCVEReport(lines, null)
  }

  @Unroll
  def 'setup failure at #stage cannot publish stale report with findings=#findings'() {
    given:
    new File(workspace, 'validate-audit-report.jq').text = new File('resources/uk/gov/hmcts/pipeline/yarn/validate-audit-report.jq').text
    writeReport(findings)
    new File(workspace, 'yarn-audit-result-formatted').text = currentReport
    currentReport = null
    failureStage = stage
    setupFailure = new IllegalStateException('synthetic setup failure')

    when:
    builder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    failure.is(setupFailure)
    wrapperCalls == 0
    0 * steps.azureCosmosDBCreateDocument(_)

    where:
    [stage, findings] << ['nvm', 'resource', 'credentials', 'cleanup'].collectMany { stage ->
      [false, true].collect { [stage, it] }
    }
  }

  def 'a fresh clean report does not carry stale suppressed findings'() {
    given:
    def expected = writeReport(false)
    new File(workspace, 'yarn-audit-known-issues-result').text = fixture('yarn-audit-report-suppressed.txt')

    when:
    builder.securityCheck()

    then:
    1 * steps.azureCosmosDBCreateDocument({ it.document.report == expected })
    !new File(workspace, 'yarn-audit-known-issues-result').exists()
  }

  def 'a successful subsequent attempt publishes only its fresh report'() {
    given:
    new File(workspace, 'validate-audit-report.jq').text = new File('resources/uk/gov/hmcts/pipeline/yarn/validate-audit-report.jq').text
    def expected = builder.prepareCVEReport(fixture('yarn-audit-report-no-issues.txt'), null)
    writeReport(true)
    new File(workspace, 'yarn-audit-result-formatted').text = currentReport
    currentReport = null
    failureStage = 'nvm'
    setupFailure = new IllegalStateException('synthetic setup failure')

    when:
    builder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    failure.is(setupFailure)
    wrapperCalls == 0
    0 * steps.azureCosmosDBCreateDocument(_)

    when:
    failureStage = null
    writeReport(false)
    builder.securityCheck()

    then:
    wrapperCalls == 1
    1 * steps.azureCosmosDBCreateDocument({ it.document.report == expected })
  }

  @Unroll
  def 'report preparation failure with auditFailed=#auditFailed preserves causal exception'() {
    given:
    writeReport(true)
    def preparationFailure = new IllegalStateException('synthetic preparation failure')
    def failedBuilder = new YarnBuilder(steps) {
      def prepareCVEReport(String issues, String knownIssues) { throw preparationFailure }
    }
    if (auditFailed) { auditFailure = new IllegalStateException('synthetic audit failure') }

    when:
    failedBuilder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    failure.is(auditFailure ?: preparationFailure)
    new File(workspace, 'yarn-audit-issues-result').text.contains('auditSummary')
    0 * steps.azureCosmosDBCreateDocument(_)

    where:
    auditFailed << [false, true]
  }

  @Unroll
  def 'invalid current nested report #name is rejected even after successful audit'() {
    given:
    writeReport(true)
    def report = new JsonSlurperClassic().parseText(currentReport)
    corrupt(report)
    currentReport = JsonOutput.toJson(report)

    when:
    builder.securityCheck()

    then:
    thrown(IllegalStateException)
    0 * steps.azureCosmosDBCreateDocument(_)

    where:
    name               | corrupt
    'missing count'    | { it.metadata.vulnerabilities.remove('high') }
    'negative count'   | { it.metadata.vulnerabilities.low = -1 }
    'fractional count' | { it.metadata.vulnerabilities.low = 0.5 }
    'string count'     | { it.metadata.vulnerabilities.low = '1' }
    'zero count'       | { it.metadata.vulnerabilities.low = 0 }
    'null entry'       | { it.advisories['1523'] = null }
    'bad identity'     | { it.advisories['1523'].id = 'different' }
    'bad severity'     | { it.advisories['1523'].severity = 'unknown' }
    'bad module'       | { it.advisories['1523'].module_name = '' }
    'bad findings'     | { it.advisories['1523'].findings = [:] }
  }

  @Unroll
  def 'array severity #severity with zero counts cannot publish with auditFailed=#auditFailed'() {
    given:
    writeReport(true)
    def report = new JsonSlurperClassic().parseText(currentReport)
    report.advisories.values().each { it.severity = severity }
    report.metadata.vulnerabilities = [info: 0, low: 0, moderate: 0, high: 0, critical: 0]
    currentReport = JsonOutput.toJson(report)
    if (auditFailed) { auditFailure = new IllegalStateException('synthetic audit failure') }

    when:
    builder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    if (auditFailed) { assert failure.is(auditFailure) }
    new File(workspace, 'yarn-audit-issues-result').text == ''
    0 * steps.azureCosmosDBCreateDocument(_)

    where:
    severity             | auditFailed
    ['low']              | false
    ['low']              | true
    ['high', 'critical'] | false
    ['high', 'critical'] | true
  }

  @Unroll
  def 'external publisher failure retains default tolerance with auditFailed=#auditFailed'() {
    given:
    writeReport(true)
    if (auditFailed) { auditFailure = new IllegalStateException('unsuppressed findings') }

    when:
    RuntimeException caught
    try { builder.securityCheck() } catch (RuntimeException failure) { caught = failure }

    then:
    caught.is(auditFailure)
    1 * steps.azureCosmosDBCreateDocument(_) >> { throw new IllegalStateException('synthetic Cosmos outage') }

    where:
    auditFailed << [false, true]
  }

  @Unroll
  def 'report read failure with auditFailed=#auditFailed preserves causal exception'() {
    given:
    writeReport(false)
    reportingFailure = new IllegalStateException('synthetic read failure')
    if (auditFailed) { auditFailure = new IllegalStateException('synthetic audit failure') }

    when:
    builder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    failure.is(auditFailure ?: reportingFailure)
    0 * steps.azureCosmosDBCreateDocument(_)

    where:
    auditFailed << [false, true]
  }

  private String fixture(String name) {
    new File(getClass().classLoader.getResource(name).toURI()).text
  }
}
