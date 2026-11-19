package uk.gov.hmcts.contino

import java.time.LocalDate
import spock.lang.Specification
import spock.lang.Unroll
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

class GradleBuilderTest extends Specification {

  static final String GRADLE_CMD = './gradlew'
  static final String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"

  def steps
  def sampleCVEReport

  def builder
  def envVars

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    envVars = [BRANCH_NAME: 'master']
    steps.getEnv() >> envVars
    builder = new GradleBuilder(steps, 'test')
    DeprecationConfig.deprecationConfigInternal = null
    WarningCollector.pipelineWarnings.clear()
    sampleCVEReport = new File(this.getClass().getClassLoader().getResource('dependency-check-report.json').toURI()).text
    steps.readFile(_ as String) >> sampleCVEReport
    // Pass the ADO_MAVEN_PAT withAzureKeyvault wrapper through to the closure body.
    steps.withAzureKeyvault(_, _) >> { args -> args[1].call() }
  }

  def cleanup() {
    DeprecationConfig.deprecationConfigInternal = null
    WarningCollector.pipelineWarnings.clear()
  }

  def "build calls 'gradle assemble'"() {
    when:
      builder.build()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('assemble') })
  }

  def "test calls 'gradle check'"() {
    when:
      builder.test()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('check') })
  }

  def "sonarScan"() {
    when:
      builder.sonarScan()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('sonarqube') })
  }

  def "fortifyScan uses gradle fortifyScan task when available"() {
    given:
      steps.fileExists('gradlew') >> true
      steps.libraryResource('uk/gov/hmcts/gradle/init.gradle') >> null
      steps.writeFile([file: 'init.gradle', text: null]) >> null
      steps.sh({ it instanceof Map && it.returnStatus == true && it.script.contains('tasks --all') }) >> 0

    when:
      builder.fortifyScan()

    then:
      0 * steps.fortifyOnDemandScan()
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('fortifyScan') })
      1 * steps.archiveArtifacts({ it.allowEmptyArchive == true && it.artifacts == 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*' })
  }

  def "fortifyScan falls back to library runner when gradle task missing"() {
    given:
      steps.fileExists('gradlew') >> true
      steps.libraryResource('uk/gov/hmcts/gradle/init.gradle') >> null
      steps.writeFile([file: 'init.gradle', text: null]) >> null
      steps.sh({ it instanceof Map && it.returnStatus == true && it.script.contains('tasks --all') }) >> 1

    when:
      builder.fortifyScan()

    then:
      1 * steps.fortifyOnDemandScan()
      0 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('fortifyScan') })
      1 * steps.archiveArtifacts({ it.allowEmptyArchive == true && it.artifacts == 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*' })
  }

  def "fortifyScan can be forced to use library runner"() {
    given:
      envVars.FORTIFY_SCAN_RUNNER = 'library'
      steps.fileExists('gradlew') >> true

    when:
      builder.fortifyScan()

    then:
      1 * steps.fortifyOnDemandScan()
      0 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('fortifyScan') })
      1 * steps.archiveArtifacts({ it.allowEmptyArchive == true && it.artifacts == 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*' })
  }

  def "smokeTest calls 'gradle smoke' with '--rerun-tasks' flag"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('smoke') && it.contains('--rerun-tasks') })
  }

  def "functionalTest calls 'gradle functional' with '--rerun-tasks' flag"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('functional') && it.contains('--rerun-tasks') })
  }

  def "functionalTest installs Yarn dependencies when generated test package is missing install state"() {
    given:
      steps.fileExists('package.json') >> true
      steps.fileExists('yarn.lock') >> true
      steps.fileExists('.yarnrc.yml') >> true
      steps.fileExists('node_modules/.yarn-state.yml') >> false
      steps.fileExists('.pnp.cjs') >> false

    when:
      builder.functionalTest()

    then:
      1 * steps.echo('Installing yarn dependencies - install state missing after agent hop')
      1 * steps.sh({ it instanceof Map && it.label == 'Install yarn dependencies' && it.script == '/usr/bin/yarn install --immutable --silent' })
      1 * steps.sh({ !(it instanceof Map) && it.startsWith(GRADLE_CMD) && it.contains('functional') && it.contains('--rerun-tasks') })
  }

  def "functionalTest skips Yarn install when dependencies are already installed"() {
    given:
      steps.fileExists('package.json') >> true
      steps.fileExists('yarn.lock') >> true
      steps.fileExists('.yarnrc.yml') >> true
      steps.fileExists('node_modules/.yarn-state.yml') >> true

    when:
      builder.functionalTest()

    then:
      0 * steps.sh({ it instanceof Map && it.label == 'Install yarn dependencies' })
      1 * steps.sh({ !(it instanceof Map) && it.startsWith(GRADLE_CMD) && it.contains('functional') && it.contains('--rerun-tasks') })
  }

  def "functionalTest skips Yarn install when project is not a Yarn Berry workspace"() {
    given:
      steps.fileExists('package.json') >> true
      steps.fileExists('yarn.lock') >> true
      steps.fileExists('.yarnrc.yml') >> false

    when:
      builder.functionalTest()

    then:
      0 * steps.echo('Installing yarn dependencies - install state missing after agent hop')
      0 * steps.sh({ it instanceof Map && it.label == 'Install yarn dependencies' })
      1 * steps.sh({ !(it instanceof Map) && it.startsWith(GRADLE_CMD) && it.contains('functional') && it.contains('--rerun-tasks') })
  }

  def "e2eTest calls 'gradle e2eTest' with '--rerun-tasks' flag"(){
    given:
      steps.publishHTML(_ as Map) >> null
    when:
      builder.e2eTest()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('e2eTest') && it.contains('--rerun-tasks') })
  }

  def "mutationTest calls 'gradle pitest' with '--rerun-tasks' flag"() {
    when:
    builder.mutationTest()
    then:
    1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('pitest') })
  }

  def "apiGatewayTest calls 'gradle api' with '--rerun-tasks' flag"() {
    when:
    builder.apiGatewayTest()
    then:
    1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('apiGateway') && it.contains('--rerun-tasks') })
  }

  def "crossBrowserTest calls 'gradle crossbrowser'"() {
    when:
    builder.crossBrowserTest()
    then:
    1 * steps.saucePublisher()
    1 * steps.withSauceConnect({ it.startsWith('reform_tunnel') }, _ as Closure)
    1 * steps.archiveArtifacts(['allowEmptyArchive':true, 'artifacts':'functional-output/**/*'])
    when:
    builder.gradle('--rerun-tasks crossbrowser')
    then:
    1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('--rerun-tasks crossbrowser') })
    1 * steps.writeFile(['file':'init.gradle', 'text':null])
    1 * steps.libraryResource('uk/gov/hmcts/gradle/init.gradle')
  }

  def "crossBrowserTest calls 'gradle crossbrowser' with 'BROWSER_GROUP' environment variable prepended"() {
    String browser = 'chrome'
    String browserGroup = "BROWSER_GROUP=${browser}"
    when:
    builder.crossBrowserTest(browser)
    then:
    1 * steps.saucePublisher()
    1 * steps.withSauceConnect({ it.startsWith('reform_tunnel') }, _ as Closure)
    1 * steps.archiveArtifacts(['allowEmptyArchive':true, 'artifacts':'functional-output/**/*'])
    when:
    builder.gradle('--rerun-tasks crossbrowser', browserGroup)
    then:
    1 * steps.sh({ it.startsWith("${browserGroup} ${GRADLE_CMD}") && it.contains('--rerun-tasks crossbrowser') })
    1 * steps.writeFile(['file':'init.gradle', 'text':null])
    1 * steps.libraryResource('uk/gov/hmcts/gradle/init.gradle')
  }

  def "securityCheck calls 'gradle dependencyCheckAggregate'"() {
    setup:
      def closure
      steps.withAzureKeyvault(_, { it.call() }) >> { closure = it }
      def spyGradleBuilder = Spy(GradleBuilder, constructorArgs: [steps, 'test']) {
        hasPlugin(_) >> true
      }
    when:
    spyGradleBuilder.securityCheck()
    then:
      1 * steps.sh({
        GString it -> it.startsWith(GRADLE_CMD) && it.contains('dependencyCheckAggregate')
      })
  }

  def "runProviderVerification triggers a gradlew hook"() {
    setup:
      def version = "v3r510n"
      def publishResults = false
    when:
      builder.runProviderVerification(PACT_BROKER_URL, version, publishResults)
    then:
      1 * steps.sh({it.startsWith(GRADLE_CMD) &&
                    it.contains("-Ppact.broker.url=${PACT_BROKER_URL} -Ppactbroker.url=${PACT_BROKER_URL} -Ppact.provider.version=${version} -Ppact.verifier.publishResults=${publishResults} runProviderPactVerification")})
  }

  def "runConsumerTests triggers a gradlew hook"() {
    setup:
      def version = "v3r510n"
    when:
      builder.runConsumerTests(PACT_BROKER_URL, version)
    then:
      1 * steps.sh({it.startsWith(GRADLE_CMD) &&
                    it.contains("-Ppact.broker.url=${PACT_BROKER_URL} -Ppactbroker.url=${PACT_BROKER_URL} -Ppact.consumer.version=${version} runAndPublishConsumerPactTests")})
  }

  def "runConsumerCanIDeploy triggers a gradlew hook"() {
    setup:
    def version = "v3r510n"
    when:
    builder.runConsumerCanIDeploy()
    then:
    1 * steps.sh({it.startsWith(GRADLE_CMD) &&
            it.contains("canideploy")})
  }

  def "Prepares CVE report for publishing to CosmosDB"() {
    when:
    def result = builder.prepareCVEReport(sampleCVEReport)

    then:
    // Report has 2 dependencies with vulnerabilities and 3 with suppressed vulnerabilities.
    result.dependencies.size() == 5
    // Only dependencies with vulnerabilities or suppressed vulnerabilities should be reported
    result.dependencies.every {
      it.vulnerabilities || it.suppressedVulnerabilities
    }
  }

  def "techStackMaintenance"() {
    when:
    builder.techStackMaintenance()
    then:
    1 * steps.echo('Running Gradle Tech stack maintenance')
  }

  @Unroll
  def "checks Java runtime #actual against global and major-specific minimums"() {
    given:
      def policy = [
        minimumVersion: '21.0.13.1',
        deadline: LocalDate.of(2099, 1, 1),
        majorVersions: ['25': [minimumVersion: '25.0.5.1', deadline: LocalDate.of(2099, 2, 1)]]
      ]

    expect:
      (GradleBuilder.findJavaRuntimeVersionDeprecation(actual, policy) == null) == supported

    where:
      actual              || supported
      '21.0.13.1+2-LTS'   || true
      '21.0.14+7-LTS'     || true
      '22+36'             || true
      '25.0.5.1+2-LTS'    || true
      '25.0.6+1-LTS'      || true
      '21.0.13.0.3+1-LTS' || false
      '21.0.13+9-LTS'     || false
      '25.0.5.0.3+1-LTS'  || false
      '25.0.5.1-ea+4'     || false
  }

  def "extracts the runtime version from distroless Java settings output"() {
    given:
      String output = '''
Property settings:
    file.encoding = UTF-8
    java.runtime.version = 21.0.8+9-LTS
openjdk version "21.0.8" 2025-07-15 LTS
'''

    expect:
      GradleBuilder.extractJavaRuntimeVersion(output) == '21.0.8+9-LTS'
  }

  def "checks Java by directly overriding the distroless image entrypoint"() {
    given:
      String imageName = 'cnpacr.azurecr.io/pcs/pcs-api:pr-123'
      def acr = Mock(Acr)
      def dockerImage = Mock(DockerImage)
      dockerImage.getBaseTaggedName() >> imageName
      dockerImage.getAcr() >> acr
      DeprecationConfig.deprecationConfigInternal = [
        java: [runtime_version: [
          version: '21.0.13.1',
          date_deadline: '2099-01-01',
          message: 'This version is affected by CVE-2026-114938.'
        ]]
      ]
      String versionOutput = '''
    java.runtime.version = 21.0.8+9-LTS
openjdk version "21.0.8" 2025-07-15 LTS
'''

    when:
      builder.checkContainerRuntimeVersion(dockerImage)

    then:
      1 * acr.login()
      1 * steps.sh({
        it instanceof Map &&
          it.label == 'Pull image for Java runtime version check' &&
          it.script == "docker pull '${imageName}'"
      })
      1 * steps.sh({
        it instanceof Map &&
          it.returnStdout &&
          it.script.contains('--entrypoint /usr/bin/java') &&
          it.script.contains('--env JAVA_TOOL_OPTIONS=') &&
          !it.script.contains('sh -c')
      }) >> versionOutput
      WarningCollector.pipelineWarnings.size() == 1
      WarningCollector.pipelineWarnings.first().warningKey == 'old_java_runtime_version'
      WarningCollector.pipelineWarnings.first().warningMessage.contains('21.0.8+9-LTS')
      WarningCollector.pipelineWarnings.first().warningMessage.contains('CVE-2026-114938')
  }

  def "reads Java runtime minimums and deadlines from deprecation config"() {
    given:
      DeprecationConfig.deprecationConfigInternal = [
        java: [runtime_version: [
          version: '21.0.13.1',
          date_deadline: '2099-01-01',
          message: 'This version is affected by CVE-2026-114938.',
          major_versions: ['25': [version: '25.0.5.1', date_deadline: '2099-02-01']]
        ]]
      ]

    when:
      Map policy = builder.getJavaRuntimeDeprecationPolicy()

    then:
      policy.minimumVersion == '21.0.13.1'
      policy.deadline == LocalDate.of(2099, 1, 1)
      policy.message == 'This version is affected by CVE-2026-114938.'
      policy.majorVersions == ['25': [minimumVersion: '25.0.5.1', deadline: LocalDate.of(2099, 2, 1)]]
  }
}
