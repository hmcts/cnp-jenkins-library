package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class YarnAuditTransformerTest extends Specification {
  File directory

  def setup() {
    directory = File.createTempDir('yarn-audit-transformer-', '')
    new File(directory, 'yarn.lock').text = '  resolution: "example@npm:1.0.0"\n'
  }

  def cleanup() {
    directory.deleteDir()
  }

  @Unroll
  def 'invalid audit input reports #errorType on stderr and fails without a JSON report'() {
    when:
    def result = transform(input)

    then:
    result.status != 0
    result.stdout == ''
    result.stderr.contains('Yarn audit transformation failed:')
    result.stderr.contains(errorType)

    where:
    input         | errorType
    '{invalid}\n' | 'SyntaxError'
    '{}\n'        | 'TypeError'
  }

  def 'valid advisory input produces only a JSON report without requesting GitHub data'() {
    given:
    def input = JsonOutput.toJson([
      value: 'example',
      children: [
        ID: '123',
        Dependents: ['application'],
        'Tree Versions': ['1.0.0'],
        Issue: 'Example advisory',
        Severity: 'high',
        'Vulnerable Versions': '<2.0.0'
      ]
    ])

    when:
    def result = transform(input)

    then:
    result.status == 0
    result.stderr == ''
    def report = new JsonSlurper().parseText(result.stdout)
    report.advisories.keySet() == ['123'] as Set
    report.advisories['123'].module_name == 'example'
    report.advisories['123'].severity == 'high'
    report.advisories['123'].findings == [[paths: ['application'], version: '1.0.0']]
    report.metadata.dependencies == 1
    report.metadata.vulnerabilities == [critical: 0, high: 1, info: 0, low: 0, moderate: 0]
  }

  def 'empty input and a lockfile with no dependencies produce a zero advisory report'() {
    given:
    new File(directory, 'yarn.lock').text = '# empty lockfile\n'

    when:
    def result = transform('')

    then:
    result.status == 0
    result.stderr == ''
    def report = new JsonSlurper().parseText(result.stdout)
    report.advisories == [:]
    report.metadata.totalDependencies == 0
    report.metadata.vulnerabilities.values().every { it == 0 }
  }

  def 'an asynchronous advisory fetch failure preserves its original diagnostic without stdout'() {
    given:
    def preload = new File(directory, 'reject-fetch.cjs')
    preload.text = "global.fetch = async () => { throw new Error('controlled advisory fetch failure'); };"
    def advisory = rawAdvisory()
    advisory.children.URL = 'https://github.com/advisories/GHSA-example'
    def input = JsonOutput.toJson(advisory)

    when:
    def result = transform(input, ['--require', preload.absolutePath])

    then:
    result.status != 0
    result.stdout == ''
    result.stderr.contains('Yarn audit transformation failed:')
    result.stderr.contains('controlled advisory fetch failure')
  }

  @Unroll
  def 'malformed raw field #field=#value fails without partial output'() {
    given:
    def advisory = rawAdvisory()
    if (field == 'value') { advisory.value = value } else { advisory.children[field] = value }

    when:
    def result = transform(JsonOutput.toJson(rawAdvisory()) + '\n' + JsonOutput.toJson(advisory))

    then:
    result.status != 0
    result.stdout == ''
    result.stderr.contains('Yarn audit transformation failed:')

    where:
    field                 | value
    'value'               | null
    'value'               | ' '
    'ID'                  | null
    'ID'                  | ' '
    'ID'                  | [:]
    'Severity'            | null
    'Severity'            | 'unknown'
    'Tree Versions'       | '1.0.0'
    'Tree Versions'       | []
    'Tree Versions'       | [null]
    'Dependents'          | 'application'
    'Dependents'          | [12]
    'Issue'               | ''
    'Vulnerable Versions' | null
    'URL'                 | 12
  }

  @Unroll
  def 'valid numeric or string advisory identity #id is retained'() {
    given:
    def advisory = rawAdvisory()
    advisory.children.ID = id

    when:
    def result = transform(JsonOutput.toJson(advisory))

    then:
    result.status == 0
    new JsonSlurper().parseText(result.stdout).advisories[id.toString()].id == id

    where:
    id << [123, 'example (deprecation)', '__proto__']
  }

  def 'handled HTTP enrichment failure retains the advisory and succeeds'() {
    given:
    def preload = new File(directory, 'http-error.cjs')
    preload.text = "global.fetch = async () => ({ok:false,status:503,text:async ()=>'synthetic outage'});"
    def advisory = rawAdvisory()
    advisory.children.URL = 'https://github.com/advisories/GHSA-example'

    when:
    def result = transform(JsonOutput.toJson(advisory), ['--require', preload.absolutePath])

    then:
    result.status == 0
    result.stderr == ''
    def report = new JsonSlurper().parseText(result.stdout)
    report.advisories['123'].title.contains('ResultCode: 503')
    report.metadata.vulnerabilities.high == 1
  }

  private static Map rawAdvisory() {
    [value: 'example', children: [ID: '123', Dependents: ['application'],
      'Tree Versions': ['1.0.0'], Issue: 'Example advisory', Severity: 'high',
      'Vulnerable Versions': '<2.0.0']]
  }

  private Map transform(String input, List<String> nodeArguments = []) {
    def inputFile = new File(directory, 'audit.json')
    def stdoutFile = new File(directory, 'stdout')
    def stderrFile = new File(directory, 'stderr')
    inputFile.text = input
    def script = new File('resources/uk/gov/hmcts/pipeline/yarn/transform-v4-to-v3-audit.cjs').absolutePath
    def process = new ProcessBuilder(['node'] + nodeArguments + [script])
      .directory(directory)
      .redirectInput(inputFile)
      .redirectOutput(stdoutFile)
      .redirectError(stderrFile)
      .start()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      throw new AssertionError('Yarn audit transformer did not finish within 10 seconds')
    }
    [status: process.exitValue(), stdout: stdoutFile.text, stderr: stderrFile.text]
  }
}
