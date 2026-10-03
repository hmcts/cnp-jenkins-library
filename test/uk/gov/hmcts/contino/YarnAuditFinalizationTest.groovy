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
  YarnBuilder builder

  def setup() {
    workspace = File.createTempDir('yarn-audit-finalizer-', '')
    steps.getEnv() >> [BRANCH_NAME: 'test']
    steps.usernamePassword(_) >> [:]
    steps.withCredentials(_, _) >> { credentials, body -> body() }
    steps.fileExists(_) >> { String path -> new File(workspace, path).exists() }
    steps.readFile(_) >> { String path ->
      if (reportingFailure) { throw reportingFailure }
      new File(workspace, path).text
    }
    steps.sh(_) >> { args ->
      if (args[0] instanceof Map) { return 0 }
      String script = args[0]
      if (script.contains('./yarn-audit-with-suppressions.sh') && auditFailure) {
        throw auditFailure
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
    if (report != null) { new File(workspace, 'yarn-audit-result-formatted').text = report }
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
  }

  def 'an unavailable report fails even when the audit command returned successfully'() {
    when:
    builder.securityCheck()

    then:
    thrown(IllegalStateException)
    0 * steps.azureCosmosDBCreateDocument(_)
  }

  def 'a reporting failure does not replace the original audit error'() {
    given:
    writeReport(false)
    auditFailure = new IllegalStateException('original audit failure')
    reportingFailure = new IllegalStateException('report read failed')

    when:
    builder.securityCheck()

    then:
    def failure = thrown(IllegalStateException)
    failure.is(auditFailure)
    0 * steps.azureCosmosDBCreateDocument(_)
  }

  @Unroll
  def 'valid report publishes when vulnerabilities=#vulnerabilities auditFailed=#auditFailed suppressed=#suppressed'() {
    given:
    def expected = writeReport(vulnerabilities)
    if (auditFailed) { auditFailure = new IllegalStateException('unsuppressed vulnerabilities') }
    if (suppressed) {
      new File(workspace, 'yarn-audit-known-issues-result').text = fixture('yarn-audit-report-suppressed.txt')
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
    new File(workspace, 'yarn-audit-result-formatted').text = JsonOutput.toJson(report)
    builder.prepareCVEReport(lines, null)
  }

  private String fixture(String name) {
    new File(getClass().classLoader.getResource(name).toURI()).text
  }
}
