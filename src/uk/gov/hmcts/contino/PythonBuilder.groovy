package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate

class PythonBuilder extends AbstractBuilder {

  private static final String PYTHON_VERSION_FILE = '.python-version'
  private static final List<String> SUPPORTED_PYTHON_VERSIONS = ['3.13']

  def localSteps

  PythonBuilder(steps) {
    super(steps)
    this.localSteps = steps
  }

  @Override
  def build() {
    addVersionInfo()
    steps.sh('uv sync --locked --link-mode=copy')
  }

  @Override
  def fortifyScan() {
    try {
      steps.fortifyOnDemandScan()
    } finally {
      steps.archiveArtifacts(allowEmptyArchive: true, artifacts: 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*')
    }
  }

  @Override
  def test() {
    try {
      steps.sh('uv run pytest tests/unit --junit-xml=test-results/unit/results.xml --cov=app --cov-report=xml -v')
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
    int exitCode
    def parsedReport = [vulnerabilities: []]
    try {
      exitCode = steps.sh(script: 'uv audit --output-format json > uv-audit-report.json', returnStatus: true)
    } finally {
      if (steps.fileExists('uv-audit-report.json')) {
        steps.archiveArtifacts(artifacts: 'uv-audit-report.json')
        String jsonReport = steps.readFile('uv-audit-report.json')
        parsedReport = prepareCVEReport(jsonReport)
        new CVEPublisher(steps).publishCVEReport('python', parsedReport)
      }
    }
    def vulns = parsedReport.vulnerabilities ?: []
    if (exitCode == 0) {
      steps.echo 'uv audit: no vulnerabilities found'
      return
    }
    if (vulns.isEmpty()) {
      steps.error('Security vulnerabilities found in Python dependencies. Review the uv-audit-report.json build artifact for details.')
    }
    def details = ["Security vulnerabilities found in Python dependencies (${vulns.size()})"]
    details.addAll(formatCVELines(vulns))
    details.add('See the uv-audit-report.json build artifact for full details, or run uv audit --output-format json locally.')
    steps.writeFile(file: 'uv-audit-summary.txt', text: "${details.join('\n')}\n")
    steps.sh(label: 'Build Failed: Python dependency vulnerabilities', script: 'cat uv-audit-summary.txt && exit 1')
  }

  def formatCVELines(vulns) {
    vulns.collect { v ->
      def fixed = (v.fix_versions ?: v.fixed_in ?: v.fixed ?: []) as List
      def aliases = (v.aliases ?: []) as List
      def aliasStr = aliases ? " [${aliases.join(', ')}]" : ''
      "  - ${v.id ?: '?'}${aliasStr} in ${v.package ?: '?'}@${v.installed ?: '?'} (fixed in: ${fixed ? fixed.join(', ') : 'n/a'})"
    }
  }

  def logCVEReport(parsedReport) {
    def vulns = parsedReport.vulnerabilities ?: []
    if (vulns.isEmpty()) {
      steps.echo 'uv audit: no vulnerabilities found'
      return
    }
    formatCVELines(vulns).each { steps.echo it }
  }

  def prepareCVEReport(String uvAuditJSON) {
    if (!uvAuditJSON?.trim()) {
      steps.echo 'uv audit: report is empty'
      return [vulnerabilities: []]
    }
    def parsed
    try {
      parsed = new JsonSlurperClassic().parseText(uvAuditJSON)
    } catch (Exception e) {
      steps.echo "uv audit: failed to parse report (${e.message}). Raw output:\n${uvAuditJSON}"
      return [vulnerabilities: []]
    }
    def vulns = (parsed instanceof Map ? parsed.vulnerabilities : parsed) ?: []
    def flat = vulns.collect { v ->
      v + [package: v.dependency?.name, installed: v.dependency?.version]
    }
    return [vulnerabilities: flat]
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
    def deprecationConfig = new DeprecationConfig(steps)
    def repoUrl = steps.env.GIT_URL
    def config = deprecationConfig.getDeprecationConfig(repoUrl)
    def pythonConfig = config?.python?.python_version
    def deadline = pythonConfig?.date_deadline ? LocalDate.parse(pythonConfig.date_deadline) : null

    if (!steps.fileExists(PYTHON_VERSION_FILE)) {
      steps.echo("[Warning] A ${PYTHON_VERSION_FILE} file is missing. Add a ${PYTHON_VERSION_FILE} file specifying your Python version, e.g. '3.13'.")
      return
    }

    String rawVersion = steps.readFile(PYTHON_VERSION_FILE).trim()
    String majorMinor = rawVersion.tokenize('.').take(2).join('.')

    if (!SUPPORTED_PYTHON_VERSIONS.contains(majorMinor)) {
      def message = "Python version '${majorMinor}' is not supported. Currently supported versions: ${SUPPORTED_PYTHON_VERSIONS.join(', ')}. Update your ${PYTHON_VERSION_FILE} file."
      if (deadline) {
        WarningCollector.addPipelineWarning('unsupported_python_version', message, deadline)
      } else {
        steps.echo("[Warning] ${message}")
      }
    }
  }
}
