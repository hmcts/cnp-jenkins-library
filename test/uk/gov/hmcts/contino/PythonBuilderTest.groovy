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
}
