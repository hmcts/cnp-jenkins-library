package uk.gov.hmcts.contino

import spock.lang.Specification
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

class PythonBuilderTest extends Specification {

  def steps
  def builder
  def envVars

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    envVars = [BRANCH_NAME: 'master']
    steps.getEnv() >> envVars
    builder = new PythonBuilder(steps)
    WarningCollector.pipelineWarnings.clear()
  }

  def cleanup() {
    WarningCollector.pipelineWarnings.clear()
  }

  def "build calls 'uv sync --locked --no-dev'"() {
    when:
      builder.build()
    then:
      1 * steps.sh({ it.contains('uv sync --locked --no-dev') })
  }

  def "build calls addVersionInfo"() {
    when:
      builder.build()
    then:
      1 * steps.sh({ it.contains('tee version') })
  }

  def "addVersionInfo writes a version file reading from pyproject.toml"() {
    when:
      builder.addVersionInfo()
    then:
      1 * steps.sh({
        it.contains('tee version') &&
        it.contains('pyproject.toml') &&
        it.contains('BUILD_NUMBER') &&
        it.contains('git rev-parse HEAD')
      })
  }

  def "test calls 'uv run pytest tests/unit' and publishes JUnit XML"() {
    when:
      builder.test()
    then:
      1 * steps.sh({ it.contains('uv run pytest tests/unit') })
      1 * steps.junit({ it instanceof Map && it.allowEmptyResults == true && it.testResults.contains('test-results/unit') })
  }

  def "test publishes JUnit XML even when pytest fails"() {
    given:
      steps.sh(_ as String) >> { throw new Exception('pytest failed') }
    when:
      builder.test()
    then:
      thrown(Exception)
      1 * steps.junit({ it instanceof Map && it.allowEmptyResults == true && it.testResults.contains('test-results/unit') })
  }

  def "smokeTest publishes JUnit XML even when pytest fails"() {
    given:
      steps.sh(_ as String) >> { throw new Exception('pytest failed') }
    when:
      builder.smokeTest()
    then:
      thrown(Exception)
      1 * steps.junit({ it instanceof Map && it.allowEmptyResults == false && it.testResults.contains('test-results/smoke') })
  }

  def "smokeTest calls 'uv run pytest tests/smoke' and publishes JUnit XML"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh({ it.contains('uv run pytest tests/smoke') })
      1 * steps.junit({ it instanceof Map && it.allowEmptyResults == false && it.testResults.contains('test-results/smoke') })
  }

  def "functionalTest calls 'uv run pytest tests/functional' and publishes JUnit XML"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh({ it.contains('uv run pytest tests/functional') })
      1 * steps.junit({ it instanceof Map && it.allowEmptyResults == true && it.testResults.contains('test-results/functional') })
  }

  def "fullFunctionalTest delegates to functionalTest"() {
    when:
      builder.fullFunctionalTest()
    then:
      1 * steps.sh({ it.contains('uv run pytest tests/functional') })
      1 * steps.junit({ it instanceof Map && it.allowEmptyResults == true && it.testResults.contains('test-results/functional') })
  }

  def "sonarScan calls sonar-scanner"() {
    when:
      builder.sonarScan()
    then:
      1 * steps.sh({ it.contains('sonar-scanner') })
  }

  def "securityCheck calls uv audit"() {
    given:
      steps.readFile('uv-audit-report.json') >> '[]'
    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ it.contains('uv audit') })
  }

  def "securityCheck publishes CVE report even when uv audit finds vulnerabilities"() {
    given:
      steps.sh(_ as String) >> { throw new Exception('uv audit exit 1') }
      steps.readFile('uv-audit-report.json') >> '[]'
    when:
      builder.securityCheck()
    then:
      thrown(Exception)
      1 * steps.readFile('uv-audit-report.json')
  }

  def "securityCheck rethrows exception on failure"() {
    given:
      steps.sh(_ as String) >> { throw new Exception('uv audit failed') }
      steps.readFile('uv-audit-report.json') >> '[]'
    when:
      builder.securityCheck()
    then:
      thrown(Exception)
  }

  def "setupToolVersion adds no warning when .python-version contains supported version 3.13"() {
    given:
      steps.fileExists('.python-version') >> true
      steps.readFile('.python-version') >> '3.13'
    when:
      builder.setupToolVersion()
    then:
      WarningCollector.pipelineWarnings.isEmpty()
  }

  def "setupToolVersion extracts major.minor from full patch version string 3.13.2"() {
    given:
      steps.fileExists('.python-version') >> true
      steps.readFile('.python-version') >> '3.13.2'
    when:
      builder.setupToolVersion()
    then:
      WarningCollector.pipelineWarnings.isEmpty()
  }

  def "setupToolVersion adds warning when .python-version contains unsupported version"() {
    given:
      steps.fileExists('.python-version') >> true
      steps.readFile('.python-version') >> '3.11'
    when:
      builder.setupToolVersion()
    then:
      WarningCollector.pipelineWarnings.size() == 1
      WarningCollector.pipelineWarnings[0].warningKey == 'unsupported_python_version'
  }

  def "setupToolVersion adds warning when .python-version file is missing"() {
    given:
      steps.fileExists('.python-version') >> false
    when:
      builder.setupToolVersion()
    then:
      WarningCollector.pipelineWarnings.size() == 1
      WarningCollector.pipelineWarnings[0].warningKey == 'missing_python_version_file'
  }

  def "fortifyScan delegates to library FoD runner"() {
    when:
      builder.fortifyScan()
    then:
      1 * steps.fortifyOnDemandScan()
      1 * steps.archiveArtifacts({ it instanceof Map && it.allowEmptyArchive == true && it.artifacts.contains('FortifyScanReport.html') })
  }

  def "e2eTest calls error with not implemented message"() {
    when:
      builder.e2eTest()
    then:
      1 * steps.error('Not implemented')
  }

  def "crossBrowserTest calls error with not implemented message"() {
    when:
      builder.crossBrowserTest()
    then:
      1 * steps.error('Not implemented')
  }

  def "mutationTest calls error with not implemented message"() {
    when:
      builder.mutationTest()
    then:
      1 * steps.error('Not implemented')
  }

  def "apiGatewayTest calls error with not implemented message"() {
    when:
      builder.apiGatewayTest()
    then:
      1 * steps.error('Not implemented')
  }
}
