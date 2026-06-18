package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate

class PythonBuilder extends AbstractBuilder {

  private static final String PYTHON_VERSION_FILE = '.python-version'
  private static final List<String> SUPPORTED_PYTHON_VERSIONS = ['3.13']
  private static final LocalDate PYTHON_VERSION_DEADLINE = LocalDate.of(2027, 1, 1)

  def localSteps

  PythonBuilder(steps) {
    super(steps)
    this.localSteps = steps
  }

  @Override
  def build() {
    addVersionInfo()
    steps.sh('uv sync --locked --no-dev')
  }

  @Override
  def fortifyScan() {}

  @Override
  def test() {}

  @Override
  def sonarScan() {}

  @Override
  def highLevelDataSetup(String dataSetupEnvironment) {}

  @Override
  def smokeTest() {}

  @Override
  def functionalTest() {}

  @Override
  def e2eTest() {
    steps.error('Not implemented')
  }

  @Override
  def apiGatewayTest() {
    steps.error('Not implemented')
  }

  @Override
  def crossBrowserTest() {
    steps.error('Not implemented')
  }

  @Override
  def mutationTest() {
    steps.error('Not implemented')
  }

  @Override
  def fullFunctionalTest() {
    functionalTest()
  }

  @Override
  def securityCheck() {}

  @Override
  def addVersionInfo() {
    steps.sh('''tee version <<EOF
version: $(grep '^version' pyproject.toml | sed 's/.*= *"//' | sed 's/".*//')
number: ${BUILD_NUMBER}
commit: $(git rev-parse HEAD)
date: $(date)
EOF
''')
  }

  @Override
  def setupToolVersion() {}
}
