package uk.gov.hmcts.contino

import spock.lang.Specification

class GradleBuilderTest extends Specification {

  static final String GRADLE_CMD = './gradlew'
  static final String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"

  def steps
  def sampleCVEReport

  def builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.getEnv() >> [
      BRANCH_NAME: 'master',
    ]
    builder = new GradleBuilder(steps, 'test')
    sampleCVEReport = new File(this.getClass().getClassLoader().getResource('dependency-check-report.json').toURI()).text
    steps.readFile(_ as String) >> sampleCVEReport
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
        GString it -> it.startsWith(GRADLE_CMD) && it.contains('dependencyCheckAggregate') &&
        it.contains('jdbc:postgresql://owaspdependency-v6-prod')
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
                    it.contains("-Ppact.broker.url=${PACT_BROKER_URL} -Ppact.provider.version=${version} -Ppact.verifier.publishResults=${publishResults} runProviderPactVerification")})
  }

  def "runConsumerTests triggers a gradlew hook"() {
    setup:
      def version = "v3r510n"
    when:
      builder.runConsumerTests(PACT_BROKER_URL, version)
    then:
      1 * steps.sh({it.startsWith(GRADLE_CMD) &&
                    it.contains("-Ppact.broker.url=${PACT_BROKER_URL} -Ppact.consumer.version=${version} runAndPublishConsumerPactTests")})
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
}
