package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.ardoq.ArdoqClient
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class PythonBuilder extends AbstractBuilder {

  // https://issues.jenkins.io/browse/JENKINS-47355 means a weird super class issue
  def localSteps

  PythonBuilder(steps) {
    super(steps)
    this.localSteps = steps
    this.securitytest = new SecurityScan(steps)
  }

  def build() {
    addVersionInfo()
    python("lint")
  }

  def fortifyScan() {
    python("fortifyScan")
  }

  def test() {
    try {
      python("test")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: '**/test-results/**/*.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: '**/htmlcov/**'
    }
  }

  def sonarScan() {
    String properties = SonarProperties.get(steps)

    steps.sh "sonar-scanner ${properties}"
  }

  def highLevelDataSetup(String dataSetupEnvironment) {
    python("highLevelDataSetup ${dataSetupEnvironment}")
  }

  def smokeTest() {
    try {
      python("test:smoke")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'smoke-output/**/*.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'smoke-output/**'
    }
  }

  def functionalTest() {
    try {
      python("test:functional")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'functional-output/**/*.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def apiGatewayTest() {
    try {
      python("test:apiGateway")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'api-output/**/*.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'api-output/**'
    }
  }

  def crossBrowserTest() {
    try {
      localSteps.withSauceConnect("reform_tunnel") {
        python("test:crossbrowser")
      }
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/crossbrowser/reports/**/*'
      localSteps.saucePublisher()
    }
  }

  def crossBrowserTest(String browser) {
    try {
      localSteps.withSauceConnect("reform_tunnel") {
        python("test:crossbrowser", "BROWSER_GROUP=$browser")
      }
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: "functional-output/$browser*/*"
      localSteps.saucePublisher()
    }
  }

  def fullFunctionalTest() {
    try {
      python("test:fullfunctional")
    } finally {
      localSteps.junit allowEmptyResults: true, testResults: 'functional-output/**/*.xml'
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**'
    }
  }

  def mutationTest() {
    try {
      python("test:mutation")
    } finally {
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'mutation-output/**/*'
    }
  }

  def securityCheck() {
    try {
      // Run pip-audit for CVE detection
      localSteps.sh """
        export PATH=\$HOME/.local/bin:\$PATH
        pip install --user pip-audit safety 2>/dev/null || true
        pip-audit --format json > pip-audit-report.json || echo '{"dependencies": []}' > pip-audit-report.json
      """
    } finally {
      String auditReport = ""
      if (localSteps.fileExists('pip-audit-report.json')) {
        auditReport = localSteps.readFile('pip-audit-report.json')
      }
      
      def cveReport = prepareCVEReport(auditReport)
      
      new CVEPublisher(localSteps)
        .publishCVEReport('python', cveReport)
        
      localSteps.archiveArtifacts allowEmptyArchive: true, artifacts: 'pip-audit-report.json'
    }
  }

  @Override
  def techStackMaintenance() {
    localSteps.echo "Running Python Tech stack maintenance"
    try {
      def secrets = [
        [ secretType: 'Secret', name: 'ardoq-api-key', version: '', envVariable: 'ARDOQ_API_KEY' ],
        [ secretType: 'Secret', name: 'ardoq-api-url', version: '', envVariable: 'ARDOQ_API_URL' ]
      ]
      localSteps.withAzureKeyvault(secrets) {
        def dependenciesFile = detectDependenciesFile()
        if (dependenciesFile) {
          def client = new ArdoqClient(localSteps.env.ARDOQ_API_KEY, localSteps.env.ARDOQ_API_URL, steps)
          client.updateDependencies(localSteps.readFile(dependenciesFile), 'python')
        }
      }
    } catch(Exception e) {
      localSteps.echo "Error running Python tech stack maintenance: ${e.getMessage()}"
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
mkdir -p version

tee version/version.txt <<EOF
version: $(python setup.py --version 2>/dev/null || echo "0.0.0")
number: ${BUILD_NUMBER}
commit: $(git rev-parse HEAD)
date: $(date)
EOF
    '''
  }

  def runProviderVerification(pactBrokerUrl, version, publish) {
    if (publish) {
      python("test:pact:verify-and-publish", "PACT_BROKER_URL=${pactBrokerUrl} PACT_PROVIDER_VERSION=${version}")
    } else {
      python("test:pact:verify", "PACT_BROKER_URL=${pactBrokerUrl} PACT_PROVIDER_VERSION=${version}")
    }
  }

  def runConsumerTests(pactBrokerUrl, version) {
    python("test:pact:run-and-publish", "PACT_BROKER_URL=${pactBrokerUrl} PACT_CONSUMER_VERSION=${version}")
  }

  def runConsumerCanIDeploy() {
    python("test:can-i-deploy:consumer")
  }

  private detectPackageManager() {
    if (localSteps.fileExists('pyproject.toml')) {
      def pyprojectContent = localSteps.readFile('pyproject.toml')
      if (pyprojectContent.contains('[tool.poetry]')) {
        return 'poetry'
      }
    }
    
    if (localSteps.fileExists('Pipfile')) {
      return 'pipenv'
    }
    
    return 'pip'
  }

  private detectDependenciesFile() {
    if (localSteps.fileExists('requirements.txt')) {
      return 'requirements.txt'
    }
    if (localSteps.fileExists('pyproject.toml')) {
      return 'pyproject.toml'
    }
    if (localSteps.fileExists('Pipfile.lock')) {
      return 'Pipfile.lock'
    }
    return null
  }

  private installDependencies() {
    def packageManager = detectPackageManager()
    
    localSteps.echo "Detected package manager: ${packageManager}"
    
    switch (packageManager) {
      case 'poetry':
        localSteps.sh """
          export PATH=\$HOME/.local/bin:\$PATH
          pip install --user poetry 2>/dev/null || true
          poetry install
        """
        break
      case 'pipenv':
        localSteps.sh """
          export PATH=\$HOME/.local/bin:\$PATH
          pip install --user pipenv 2>/dev/null || true
          pipenv install --dev
        """
        break
      default: // pip
        if (localSteps.fileExists('requirements.txt')) {
          localSteps.sh """
            export PATH=\$HOME/.local/bin:\$PATH
            pip install -r requirements.txt
          """
        }
        if (localSteps.fileExists('requirements-dev.txt')) {
          localSteps.sh """
            export PATH=\$HOME/.local/bin:\$PATH
            pip install -r requirements-dev.txt
          """
        }
        break
    }
  }

  private runPython(String task, String prepend = "") {
    def packageManager = detectPackageManager()
    
    if (prepend && !prepend.endsWith(' ')) {
      prepend += ' '
    }

    def command = ""
    switch (packageManager) {
      case 'poetry':
        command = "poetry run ${task}"
        break
      case 'pipenv':
        command = "pipenv run ${task}"
        break
      default:
        command = task
        break
    }

    localSteps.sh """
      export PATH=\$HOME/.local/bin:\$PATH
      ${prepend}${command}
    """
  }

  def python(String task, String prepend = "") {
    if (!localSteps.fileExists('.python_dependencies_installed')) {
      localSteps.sh("touch .python_dependencies_installed")
      installDependencies()
    }
    runPython(task, prepend)
  }

  @Override
  def securityScan() {
    if (localSteps.fileExists(".ci/security.sh")) {
      // hook to allow teams to override the default `security.sh` that we provide
      localSteps.writeFile(file: 'security.sh', text: localSteps.readFile('.ci/security.sh'))
    } else if (localSteps.fileExists("security.sh")) {
      WarningCollector.addPipelineWarning("security.sh_moved", "Please remove security.sh from root of repository, no longer needed as it has been moved to the Jenkins library", LocalDate.of(2023, 04, 17))
    } else {
      localSteps.writeFile(file: 'security.sh', text: localSteps.libraryResource('uk/gov/hmcts/pipeline/security/backend/security.sh'))
    }
    this.securitytest.execute()
  }

  @Override
  def setupToolVersion() {
    def pythonVersion = detectPythonVersion()
    
    if (pythonVersion) {
      localSteps.echo "Detected Python version: ${pythonVersion}"
      
      // Try to use pyenv if available
      def pyenvStatus = localSteps.sh(script: 'command -v pyenv', returnStatus: true)
      if (pyenvStatus == 0) {
        localSteps.sh """
          export PYENV_ROOT="\$HOME/.pyenv"
          export PATH="\$PYENV_ROOT/bin:\$PATH"
          eval "\$(pyenv init -)"
          pyenv install -s ${pythonVersion}
          pyenv local ${pythonVersion}
        """
      }
    }
    
    localSteps.sh "python --version || python3 --version"
  }

  private detectPythonVersion() {
    // Check .python-version (pyenv)
    if (localSteps.fileExists('.python-version')) {
      return localSteps.readFile('.python-version').trim()
    }
    
    // Check runtime.txt
    if (localSteps.fileExists('runtime.txt')) {
      def runtime = localSteps.readFile('runtime.txt').trim()
      if (runtime.startsWith('python-')) {
        return runtime.replace('python-', '')
      }
    }
    
    // Check pyproject.toml
    if (localSteps.fileExists('pyproject.toml')) {
      def pyproject = localSteps.readFile('pyproject.toml')
      def match = pyproject =~ /python\s*=\s*"[^"]*?(\d+\.\d+(?:\.\d+)?)"/
      if (match) {
        return match[0][1]
      }
    }
    
    return null
  }
}
