package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
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

  private Map audit(int status) {
    def yarn = new File(directory, 'yarn')
    yarn.text = "#!/usr/bin/env bash\nexit ${status}\n"
    assert yarn.setExecutable(true)
    def stdout = new File(directory, 'stdout')
    def stderr = new File(directory, 'stderr')
    def script = new File('resources/uk/gov/hmcts/pipeline/yarn/yarn-audit-with-suppressions.sh').absolutePath
    def builder = new ProcessBuilder('bash', script, 'local')
      .directory(directory)
      .redirectOutput(stdout)
      .redirectError(stderr)
    builder.environment().put('PATH', directory.absolutePath + File.pathSeparator + System.getenv('PATH'))
    builder.environment().put('YARN_VERSION', '4')
    def process = builder.start()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      throw new AssertionError('Yarn audit wrapper did not finish within 10 seconds')
    }
    [status: process.exitValue(), stdout: stdout.text, stderr: stderr.text]
  }
}
