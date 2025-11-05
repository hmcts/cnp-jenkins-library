package uk.gov.hmcts.contino

import spock.lang.Specification

class PythonBuilderTest extends Specification {

  static final String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"

  def steps

  def builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.getEnv() >> [
      BRANCH_NAME: 'master',
    ]
    steps.fileExists(_ as String) >> false
    steps.readFile(_ as String) >> '{"dependencies": []}'
    builder = new PythonBuilder(steps)
  }

  def "build calls python lint and addVersionInfo"() {
    when:
      builder.build()
    then:
      1 * steps.sh({ it.contains('touch .python_dependencies_installed') })
      1 * steps.sh({ it.contains('lint') })
  }

  def "test runs tests and archives results"() {
    when:
      builder.test()
    then:
      1 * steps.sh({ it.contains('test') })
      1 * steps.junit(['allowEmptyResults': true, 'testResults': '**/test-results/**/*.xml'])
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': '**/htmlcov/**'])
  }

  def "sonarScan calls sonar-scanner"() {
    when:
      builder.sonarScan()
    then:
      1 * steps.sh({ it.contains('sonar-scanner') })
  }

  def "smokeTest runs smoke tests"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh({ it.contains('test:smoke') })
      1 * steps.junit(['allowEmptyResults': true, 'testResults': 'smoke-output/**/*.xml'])
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': 'smoke-output/**'])
  }

  def "functionalTest runs functional tests"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh({ it.contains('test:functional') })
      1 * steps.junit(['allowEmptyResults': true, 'testResults': 'functional-output/**/*.xml'])
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': 'functional-output/**'])
  }

  def "apiGatewayTest runs API gateway tests"() {
    when:
      builder.apiGatewayTest()
    then:
      1 * steps.sh({ it.contains('test:apiGateway') })
      1 * steps.junit(['allowEmptyResults': true, 'testResults': 'api-output/**/*.xml'])
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': 'api-output/**'])
  }

  def "crossBrowserTest runs crossbrowser tests"() {
    when:
      builder.crossBrowserTest()
    then:
      1 * steps.withSauceConnect({ it.startsWith('reform_tunnel') }, _ as Closure)
    when:
      builder.python("test:crossbrowser")
    then:
      1 * steps.sh({ it.contains('test:crossbrowser') })
  }

  def "crossBrowserTest with browser parameter"() {
    when:
      builder.crossBrowserTest('chrome')
    then:
      1 * steps.sh({ it.contains('BROWSER_GROUP=chrome') && it.contains('test:crossbrowser') })
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, artifacts: "functional-output/chrome*/*"])
      1 * steps.saucePublisher()
  }

  def "mutationTest runs mutation tests"() {
    when:
      builder.mutationTest()
    then:
      1 * steps.sh({ it.contains('test:mutation') })
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': 'mutation-output/**/*'])
  }

  def "securityCheck runs pip-audit"() {
    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ it.contains('pip-audit') })
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': 'pip-audit-report.json'])
  }

  def "fullFunctionalTest runs full functional tests"() {
    when:
      builder.fullFunctionalTest()
    then:
      1 * steps.sh({ it.contains('test:fullfunctional') })
      1 * steps.junit(['allowEmptyResults': true, 'testResults': 'functional-output/**/*.xml'])
      1 * steps.archiveArtifacts(['allowEmptyArchive': true, 'artifacts': 'functional-output/**'])
  }

  def "runProviderVerification triggers pact verification with publish"() {
    setup:
      def version = "v3r510n"
      def publishResults = true
    when:
      builder.runProviderVerification(PACT_BROKER_URL, version, publishResults)
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL}") && it.contains("PACT_PROVIDER_VERSION=${version}") && it.contains("test:pact:verify-and-publish") })
  }

  def "runProviderVerification triggers pact verification without publish"() {
    setup:
      def version = "v3r510n"
      def publishResults = false
    when:
      builder.runProviderVerification(PACT_BROKER_URL, version, publishResults)
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL}") && it.contains("PACT_PROVIDER_VERSION=${version}") && it.contains("test:pact:verify") && !it.contains("verify-and-publish") })
  }

  def "runConsumerTests triggers pact consumer tests"() {
    setup:
      def version = "v3r510n"
    when:
      builder.runConsumerTests(PACT_BROKER_URL, version)
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL}") && it.contains("PACT_CONSUMER_VERSION=${version}") && it.contains("test:pact:run-and-publish") })
  }

  def "runConsumerCanIDeploy triggers can-i-deploy"() {
    when:
      builder.runConsumerCanIDeploy()
    then:
      1 * steps.sh({ it.contains("test:can-i-deploy:consumer") })
  }

  def "prepareCVEReport converts pip-audit format to standard format"() {
    given:
      def auditReport = '''
{
  "dependencies": [
    {
      "name": "requests",
      "version": "2.25.0",
      "vulns": [
        {
          "id": "PYSEC-2021-59",
          "aliases": ["CVE-2021-33503"],
          "severity": "high",
          "url": "https://pypi.org/project/requests",
          "fix_versions": ["2.25.1"]
        }
      ]
    }
  ]
}
'''
    when:
      def result = builder.prepareCVEReport(auditReport)
    then:
      result.vulnerabilities.size() == 1
      result.vulnerabilities[0].title == 'PYSEC-2021-59'
      result.vulnerabilities[0].cves == ['CVE-2021-33503']
      result.vulnerabilities[0].module_name == 'requests'
      result.vulnerabilities[0].severity == 'high'
  }

  def "prepareCVEReport handles empty report"() {
    when:
      def result = builder.prepareCVEReport("")
    then:
      result.dependencies == []
  }

  def "techStackMaintenance runs Ardoq integration"() {
    when:
      builder.techStackMaintenance()
    then:
      1 * steps.echo('Running Python Tech stack maintenance')
  }

  def "setupToolVersion detects Python version from .python-version"() {
    given:
      steps.fileExists('.python-version') >> true
      steps.readFile('.python-version') >> '3.11.0'
      steps.sh(_ as String, _ as Boolean) >> 0
    when:
      builder.setupToolVersion()
    then:
      1 * steps.echo('Detected Python version: 3.11.0')
  }

  def "setupToolVersion detects Python version from runtime.txt"() {
    given:
      steps.fileExists('.python-version') >> false
      steps.fileExists('runtime.txt') >> true
      steps.readFile('runtime.txt') >> 'python-3.10.5'
    when:
      builder.setupToolVersion()
    then:
      1 * steps.echo('Detected Python version: 3.10.5')
  }

  def "setupToolVersion detects Python version from pyproject.toml"() {
    given:
      steps.fileExists('.python-version') >> false
      steps.fileExists('runtime.txt') >> false
      steps.fileExists('pyproject.toml') >> true
      steps.readFile('pyproject.toml') >> '''
[tool.poetry]
name = "test"
python = "^3.9"
'''
    when:
      builder.setupToolVersion()
    then:
      1 * steps.echo('Detected Python version: 3.9')
  }

  def "python command installs dependencies on first run with pip"() {
    given:
      steps.fileExists('.python_dependencies_installed') >> false
      steps.fileExists('pyproject.toml') >> false
      steps.fileExists('Pipfile') >> false
      steps.fileExists('requirements.txt') >> true
    when:
      builder.python('test')
    then:
      1 * steps.sh({ it.contains('touch .python_dependencies_installed') })
      1 * steps.echo('Detected package manager: pip')
      1 * steps.sh({ it.contains('pip install -r requirements.txt') })
      1 * steps.sh({ it.contains('test') })
  }

  def "python command installs dependencies with poetry"() {
    given:
      steps.fileExists('.python_dependencies_installed') >> false
      steps.fileExists('pyproject.toml') >> true
      steps.readFile('pyproject.toml') >> '[tool.poetry]'
    when:
      builder.python('test')
    then:
      1 * steps.sh({ it.contains('touch .python_dependencies_installed') })
      1 * steps.echo('Detected package manager: poetry')
      1 * steps.sh({ it.contains('poetry install') })
      1 * steps.sh({ it.contains('poetry run test') })
  }

  def "python command installs dependencies with pipenv"() {
    given:
      steps.fileExists('.python_dependencies_installed') >> false
      steps.fileExists('pyproject.toml') >> false
      steps.fileExists('Pipfile') >> true
    when:
      builder.python('test')
    then:
      1 * steps.sh({ it.contains('touch .python_dependencies_installed') })
      1 * steps.echo('Detected package manager: pipenv')
      1 * steps.sh({ it.contains('pipenv install --dev') })
      1 * steps.sh({ it.contains('pipenv run test') })
  }
}
