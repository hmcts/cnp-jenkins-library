package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic
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
  def test() {
    try {
      steps.sh('uv run pytest tests/unit --junit-xml=test-results/unit/results.xml -v')
    } finally {
      steps.junit(allowEmptyResults: true, testResults: 'test-results/unit/*.xml')
    }
  }

  @Override
  def sonarScan() {
    String properties = SonarProperties.get(steps)
    steps.sh("sonar-scanner ${properties}")
  }

  @Override
  def highLevelDataSetup(String dataSetupEnvironment) {}

  @Override
  def smokeTest() {
    try {
      steps.sh('uv run pytest tests/smoke --junit-xml=test-results/smoke/results.xml -v')
    } finally {
      steps.junit(allowEmptyResults: false, testResults: 'test-results/smoke/*.xml')
    }
  }

  @Override
  def functionalTest() {
    try {
      steps.sh('uv run pytest tests/functional --junit-xml=test-results/functional/results.xml -v')
    } finally {
      steps.junit(allowEmptyResults: true, testResults: 'test-results/functional/*.xml')
    }
  }

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
  def securityCheck() {
    try {
      steps.sh('uv run pip-audit --format json -o pip-audit-report.json')
      String jsonReport = steps.readFile('pip-audit-report.json')
      def parsedReport = prepareCVEReport(jsonReport)
      new CVEPublisher(steps).publishCVEReport('python', parsedReport)
    } catch (Exception e) {
      steps.echo("Security check failed: ${e.message}")
      throw e
    }
  }

  def prepareCVEReport(String pipAuditJSON) {
    if (!pipAuditJSON || pipAuditJSON.trim().isEmpty()) {
      return [vulnerabilities: []]
    }

    try {
      def reportArray = new JsonSlurperClassic().parseText(pipAuditJSON)
      return [vulnerabilities: reportArray ?: []]
    } catch (Exception e) {
      return [vulnerabilities: []]
    }
  }

  @Override
  def addVersionInfo() {
    steps.sh('''tee version <<EOF
version: $(grep -m 1 '^version' pyproject.toml | sed "s/.*= *//" | tr -d '"' | tr -d "'")
number: ${BUILD_NUMBER}
commit: $(git rev-parse HEAD)
date: $(date)
EOF
''')
  }

  @Override
  def setupToolVersion() {
    if (!steps.fileExists(PYTHON_VERSION_FILE)) {
      WarningCollector.addPipelineWarning(
        'missing_python_version_file',
        "A ${PYTHON_VERSION_FILE} file is missing. Add a ${PYTHON_VERSION_FILE} file specifying your Python version, e.g. '3.13'. See https://github.com/hmcts/fastapi-template for reference.",
        PYTHON_VERSION_DEADLINE
      )
      return
    }

    String rawVersion = steps.readFile(PYTHON_VERSION_FILE).trim()
    String majorMinor = rawVersion.tokenize('.').take(2).join('.')

    if (!SUPPORTED_PYTHON_VERSIONS.contains(majorMinor)) {
      WarningCollector.addPipelineWarning(
        'unsupported_python_version',
        "Python version '${majorMinor}' is not supported. Currently supported versions: ${SUPPORTED_PYTHON_VERSIONS.join(', ')}. Update your ${PYTHON_VERSION_FILE} file. See https://github.com/hmcts/fastapi-template for reference.",
        PYTHON_VERSION_DEADLINE
      )
    }
  }
}
