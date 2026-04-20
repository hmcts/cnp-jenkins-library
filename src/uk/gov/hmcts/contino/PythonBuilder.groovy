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

  def product

  // https://issues.jenkins.io/browse/JENKINS-47355 means a weird super class issue
  def localSteps

  PythonBuilder(steps, product) {
    super(steps)
    this.product = product
    this.localSteps = steps
  }

  def build() {
    addVersionInfo()
    if (localSteps.fileExists('pyproject.toml')) {
      uv("sync")
    } else if (localSteps.fileExists('requirements.txt')) {
      uv("pip install -r requirements.txt")
      if (localSteps.fileExists('requirements-dev.txt')) {
        uv("pip install -r requirements-dev.txt")
      }
    } else {
      localSteps.error "No dependency file found. Add a pyproject.toml (recommended) or requirements.txt"
    }
    localSteps.sh(script: "uv run python -m compileall . -q", label: "Compile Python sources")
  }

  def fortifyScan() {
    try {
      String runner = (localSteps.env.FORTIFY_SCAN_RUNNER ?: 'auto').toString().trim().toLowerCase()
      if (runner == 'library') {
        localSteps.echo('Fortify: using library FoD scan runner (FORTIFY_SCAN_RUNNER=library)')
        localSteps.fortifyOnDemandScan()
      } else {
        localSteps.echo("Fortify: using library FoD scan runner (default for Python)")
        localSteps.fortifyOnDemandScan()
      }
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*'
    }
  }

  def test() {
    try {
      uv("run pytest --junitxml=test-results/unit/results.xml")
    } finally {
      localSteps.junit 'test-results/unit/results.xml'
    }
  }

  def sonarScan() {
    String properties = SonarProperties.get(localSteps)
    localSteps.sh "sonar-scanner ${properties}"
  }

  def highLevelDataSetup(String dataSetupEnvironment) {
    uv("run python -m setup_data ${dataSetupEnvironment}")
  }

  def smokeTest() {
    try {
      uv("run pytest tests/smoke --junitxml=smoke-output/results.xml")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'smoke-output/results.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'smoke-output/**'
    }
  }

  def functionalTest() {
    try {
      uv("run pytest tests/functional --junitxml=functional-output/results.xml")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'functional-output/results.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def e2eTest() {
    try {
      uv("run pytest tests/e2e --junitxml=e2e-output/results.xml")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'e2e-output/results.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'e2e-output/**'
    }
  }

  def apiGatewayTest() {
    try {
      uv("run pytest tests/api --junitxml=api-output/results.xml")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'api-output/results.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'api-output/**'
    }
  }

  def crossBrowserTest() {
    localSteps.error "Not implemented for Python"
  }

  def crossBrowserTest(String browser) {
    localSteps.error "Not implemented for Python"
  }

  def mutationTest() {
    localSteps.error "Not implemented for Python"
  }

  def securityCheck() {
    try {
      uv("run pip-audit --format=json --output=pip-audit-report.json")
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'pip-audit-report.json'

      if (localSteps.fileExists('pip-audit-report.json')) {
        String auditReport = localSteps.readFile('pip-audit-report.json')
        def cveReport = prepareCVEReport(auditReport)
        new CVEPublisher(localSteps)
          .publishCVEReport('python', cveReport)
      }
    }
  }

  def prepareCVEReport(String auditReportJSON) {
    if (!auditReportJSON || auditReportJSON.trim().isEmpty()) {
      return [dependencies: []]
    }

    try {
      def report = new JsonSlurperClassic().parseText(auditReportJSON)

      // Convert pip-audit format to our standard format
      def vulnerabilities = []
      if (report.dependencies) {
        report.dependencies.each { dep ->
          if (dep.vulns) {
            dep.vulns.each { vuln ->
              vulnerabilities << [
                title: vuln.id,
                cves: vuln.aliases ?: [],
                vulnerable_versions: dep.version,
                patched_versions: vuln.fix_versions?.join(', ') ?: 'None',
                severity: vuln.severity ?: 'unknown',
                url: vuln.url ?: '',
                module_name: dep.name
              ]
            }
          }
        }
      }

      return [
        vulnerabilities: vulnerabilities,
        summary: [
          total: vulnerabilities.size()
        ]
      ]
    } catch (Exception e) {
      return [dependencies: []]
    }
  }

  @Override
  def addVersionInfo() {
    localSteps.sh '''
mkdir -p build

tee build/build-info.json <<EOF 2>/dev/null
{
  "build_version": "$(grep -oP 'version\\s*=\\s*"\\K[^"]+' pyproject.toml || echo 'unknown')",
  "build_number": "${BUILD_NUMBER}",
  "build_commit": "$(git rev-parse HEAD)",
  "build_date": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

'''
  }

  @Override
  def techStackMaintenance() {
    localSteps.echo "Running Python tech stack maintenance"
  }

  def fullFunctionalTest() {
    functionalTest()
  }

  def dbMigrate(String vaultName, String microserviceName) {
    def secrets = [
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-DATABASE", version: '', envVariable: 'POSTGRES_DATABASE' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-HOST", version: '', envVariable: 'POSTGRES_HOST' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-PASS", version: '', envVariable: 'POSTGRES_PASS' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-PORT", version: '', envVariable: 'POSTGRES_PORT' ],
      [ secretType: 'Secret', name: "${microserviceName}-POSTGRES-USER", version: '', envVariable: 'POSTGRES_USER' ]
    ]

    def azureKeyVaultURL = "https://${vaultName}.vault.azure.net"

    localSteps.azureKeyVault(secrets: secrets, keyVaultURL: azureKeyVaultURL) {
      uv("run alembic upgrade head")
    }
  }

  @Override
  def setupToolVersion() {
    def pythonVersion

    if (localSteps.fileExists(PYTHON_VERSION_FILE)) {
      pythonVersion = localSteps.readFile(PYTHON_VERSION_FILE).trim()
      localSteps.echo "Found ${PYTHON_VERSION_FILE} requesting Python ${pythonVersion}"
    } else if (localSteps.fileExists('pyproject.toml')) {
      pythonVersion = localSteps.sh(
        script: "grep -oP 'requires-python\\s*=\\s*\"\\K[^\"]+' pyproject.toml || echo ''",
        returnStdout: true
      ).trim()
      if (pythonVersion) {
        localSteps.echo "Found pyproject.toml requires-python: ${pythonVersion}"
      }
    }

    if (!pythonVersion) {
      localSteps.error "No Python version specified. Add a .python-version file or requires-python to pyproject.toml"
    }

    nagAboutUnsupportedPythonVersion(pythonVersion)

    localSteps.sh(script: "uv python install", label: "Install Python version")
    localSteps.sh(script: "uv run python --version", label: "Verify Python version")
  }

  private nagAboutUnsupportedPythonVersion(String pythonVersion) {
    // Extract major.minor from version string, stripping any constraint operators (>=, ==, ~=)
    def versionMatch = (pythonVersion =~ /(?:>=|==|~=|>|<|!=|<=)?\s*(\d+\.\d+)/)
    if (!versionMatch.find()) {
      return
    }
    String majorMinor = versionMatch[0][1]

    if (!SUPPORTED_PYTHON_VERSIONS.contains(majorMinor)) {
      def deprecationConfig = new DeprecationConfig(localSteps)
      def repoUrl = localSteps.env.GIT_URL
      def config = deprecationConfig.getDeprecationConfig(repoUrl)
      def pythonConfig = config?.python?.("python${majorMinor.replace('.', '')}")
      def deadline = pythonConfig?.date_deadline ? LocalDate.parse(pythonConfig.date_deadline) : LocalDate.of(2026, 9, 1)

      WarningCollector.addPipelineWarning("python_${majorMinor}_unsupported",
        "Python ${majorMinor} is not supported. " +
          "Please upgrade to a supported version (${SUPPORTED_PYTHON_VERSIONS.join(', ')}). " +
          "Update your .python-version file or requires-python in pyproject.toml.", deadline
      )
    }
  }

  def uv(String task) {
    localSteps.sh(script: "uv ${task}", label: "uv ${task}")
  }
}
