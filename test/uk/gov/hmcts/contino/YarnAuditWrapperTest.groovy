package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class YarnAuditWrapperTest extends Specification {
  File directory

  def setup() {
    directory = File.createTempDir('yarn-audit-wrapper-', '')
    new File(directory, 'yarn.lock').text = '# empty lockfile\n'
  }

  def cleanup() {
    directory.deleteDir()
  }

  def 'successful empty Yarn output replaces a stale report with a valid zero advisory report'() {
    given:
    new File(directory, 'yarn-audit-result-formatted').text = '{"stale":true}'

    when:
    def result = audit(0)

    then:
    result.status == 0
    def report = new JsonSlurper().parse(new File(directory, 'yarn-audit-result-formatted'))
    report.advisories == [:]
    report.metadata.totalDependencies == 0
    report.metadata.vulnerabilities.values().every { it == 0 }
  }

  @Unroll
  def 'empty Yarn failure with status #status fails and removes stale report'() {
    given:
    new File(directory, 'yarn-audit-result-formatted').text = '{"stale":true}'

    when:
    def result = audit(status)

    then:
    result.status == status
    result.stderr.contains("yarn audit failed with exit code ${status}")
    !new File(directory, 'yarn-audit-result-formatted').exists()

    where:
    status << [1, 42]
  }

  @Unroll
  def 'Yarn #version status #status with #kind output returns #expected'() {
    given:
    def output = kind == 'empty' ? '' : kind == 'invalid' ? '{invalid}' : JsonOutput.toJson(report(kind == 'findings'))

    when:
    def result = audit(status, output, version)

    then:
    result.status == expected
    result.stderr.contains('synthetic yarn stderr')
    new File(directory, 'yarn-arguments').text.trim() == "npm audit ${version == '4' ? '--recursive' : '--all'} --environment production --json"
    new File(directory, 'yarn-audit-result-formatted').exists() == retained

    where:
    [version, status, kind, expected, retained] << ['3', '4'].collectMany { version ->
      [[0, 'empty', 0, true], [0, 'zero', 0, true], [0, 'findings', 1, true],
       [0, 'invalid', 1, false], [1, 'empty', 1, false], [1, 'zero', 1, false],
       [1, 'findings', 1, true], [1, 'invalid', 1, false],
       [42, 'empty', 42, false], [42, 'zero', 42, false],
       [42, 'findings', 42, false], [42, 'invalid', 42, false]].collect { [version] + it }
    }
  }

  @Unroll
  def 'zero advisories with extra=#extra status=#status baseline=#baseline retain only successful reports'() {
    given:
    def input = report(false)
    input.metadata.vulnerabilities.extra = extra
    if (baseline) { new File(directory, 'yarn-audit-known-issues').text = JsonOutput.toJson(report(true)) }

    when:
    def result = audit(status, JsonOutput.toJson(input), '3')

    then:
    result.status == status
    result.stderr.contains('synthetic yarn stderr')
    new File(directory, 'yarn-audit-result-formatted').exists() == (status == 0)

    where:
    extra     | status | baseline
    1         | 1      | true
    'failure' | 1      | true
    1         | 1      | false
    'failure' | 1      | false
    1         | 0      | false
    'failure' | 0      | false
  }

  @Unroll
  def 'array severity #severity with zero counts fails closed'() {
    given:
    def input = report(true, [severity: severity])
    input.metadata.vulnerabilities.low = 0

    when:
    def result = audit(0, JsonOutput.toJson(input), '3')

    then:
    result.status != 0
    !new File(directory, 'yarn-audit-result-formatted').exists()

    where:
    severity << [['low'], ['high', 'critical']]
  }

  @Unroll
  def 'invalid vulnerability count #count fails closed'() {
    given:
    def malformed = report(false)
    malformed.metadata.vulnerabilities.low = count

    when:
    def result = audit(0, JsonOutput.toJson(malformed), '3')

    then:
    result.status != 0
    !new File(directory, 'yarn-audit-result-formatted').exists()

    where:
    count << [null, '0', -1, 0.5, 1]
  }

  @Unroll
  def 'invalid formatted structure #name fails closed'() {
    when:
    def result = audit(0, input, '3')

    then:
    result.status != 0
    !new File(directory, 'yarn-audit-result-formatted').exists()

    where:
    name             | input
    'missing counts' | '{"metadata":{},"advisories":{}}'
    'multiple JSON'  | JsonOutput.toJson(report(false)) + '\n' + JsonOutput.toJson(report(false))
    'bad identity'   | JsonOutput.toJson(report(true, [id: 'different']))
    'bad severity'   | JsonOutput.toJson(report(true, [severity: 'unknown']))
    'bad module'     | JsonOutput.toJson(report(true, [module_name: ' ']))
    'bad title'      | JsonOutput.toJson(report(true, [title: null]))
    'bad range'      | JsonOutput.toJson(report(true, [vulnerable_versions: null]))
    'bad findings'   | JsonOutput.toJson(report(true, [findings: [:]]))
    'bad version'    | JsonOutput.toJson(report(true, [findings: [[version: null, paths: ['app']]]]))
    'bad paths'      | JsonOutput.toJson(report(true, [findings: [[version: '1', paths: 'app']]]))
    'zero findings'  | JsonOutput.toJson(report(true)).replace('"low":1', '"low":0')
    'infinite count' | JsonOutput.toJson(report(true)).replace('"low":1', '"low":1e999')
  }

  @Unroll
  def 'Yarn #version suppression #mode preserves findings status #expected'() {
    given:
    def first = [value: 'example', children: [ID: '123', Dependents: ['application'],
      'Tree Versions': ['1.0.0'], Issue: 'Example advisory', Severity: 'high', 'Vulnerable Versions': '<2.0.0']]
    def second = [value: 'other', children: first.children + [ID: '456']]
    def legacy = report(true)
    legacy.advisories['456'] = legacy.advisories['123'] + [id: 456]
    legacy.metadata.vulnerabilities.low = 2
    def legacyKnown = report(mode != 'none')
    if (mode == 'all') {
      legacyKnown.advisories = ['456': legacy.advisories['456'], '123': legacy.advisories['123']]
      legacyKnown.metadata.vulnerabilities.low = 2
    }
    String current = version == '4' ? [first, second].collect { JsonOutput.toJson(it) }.join('\n') : JsonOutput.toJson(legacy)
    String known = version == '4' ? (mode == 'all' ? [second, first] : [first]).collect { JsonOutput.toJson(it) }.join('\n') : JsonOutput.toJson(legacyKnown)
    new File(directory, 'yarn-audit-known-issues').text = known

    when:
    def result = audit(1, current, version)

    then:
    result.status == expected
    new JsonSlurper().parse(new File(directory, 'yarn-audit-result-formatted')).advisories.size() == 2

    where:
    version | mode  | expected
    '3'     | 'all' | 0
    '3'     | 'some'| 1
    '3'     | 'none'| 1
    '4'     | 'all' | 0
    '4'     | 'some'| 1
  }

  @Unroll
  def 'scalar severity #severity allows legacy path count beyond advisory count and shell integer range'() {
    given:
    def input = report(true, [severity: severity])
    input.metadata.vulnerabilities.low = 0
    input.metadata.vulnerabilities[severity] = 1e20

    when:
    def result = audit(1, JsonOutput.toJson(input), '3')

    then:
    result.status == 1
    result.stdout.contains('Security vulnerabilities were found')
    new File(directory, 'yarn-audit-result-formatted').exists()

    where:
    severity << ['info', 'low', 'moderate', 'high', 'critical']
  }

  @Unroll
  def 'staged Jenkins resources validate report with expected status #expected'() {
    when:
    def result = audit(0, output, '4', true)

    then:
    result.status == expected
    new File(directory, 'yarn-audit-result-formatted').exists() == (expected == 0)

    where:
    output                                    | expected
    JsonOutput.toJson(report(false))           | 0
    '{"metadata":{},"advisories":{}}'          | 1
  }

  private static Map report(boolean findings, Map changes = [:]) {
    [actions: [], advisories: findings ? ['123': [id: 123, severity: 'low', module_name: 'example',
      title: 'Example advisory', vulnerable_versions: '<2.0.0',
      findings: [[version: '1.0.0', paths: ['application']]]] + changes] : [:],
     metadata: [vulnerabilities: [info: 0, low: findings ? 1 : 0, moderate: 0, high: 0, critical: 0]]]
  }

  private Map audit(int status, String output = '', String version = '4', boolean staged = false) {
    new File(directory, 'yarn-output').text = output
    def yarn = new File(directory, 'yarn')
    yarn.text = '#!/usr/bin/env bash\nprintf "%s\\n" "$*" > yarn-arguments\ncat yarn-output\necho "synthetic yarn stderr" >&2\n' + "exit ${status}\n"
    assert yarn.setExecutable(true)
    def stdout = new File(directory, 'stdout')
    def stderr = new File(directory, 'stderr')
    def script = new File('resources/uk/gov/hmcts/pipeline/yarn/yarn-audit-with-suppressions.sh').absolutePath
    if (staged) {
      new File('resources/uk/gov/hmcts/pipeline/yarn').eachFile { resource ->
        new File(directory, resource.name).bytes = resource.bytes
      }
    }
    def builder = new ProcessBuilder(staged ? ['bash', './yarn-audit-with-suppressions.sh'] : ['bash', script, 'local'])
      .directory(directory)
      .redirectOutput(stdout)
      .redirectError(stderr)
    builder.environment().put('PATH', directory.absolutePath + File.pathSeparator + System.getenv('PATH'))
    builder.environment().put('YARN_VERSION', version)
    def process = builder.start()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      throw new AssertionError('Yarn audit wrapper did not finish within 10 seconds')
    }
    [status: process.exitValue(), stdout: stdout.text, stderr: stderr.text]
  }
}
