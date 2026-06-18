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
}
