package uk.gov.hmcts.contino

import spock.lang.Specification

class GradleBuilderTest extends Specification {

  static final String GRADLE_CMD = './gradlew'
  static final String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"

  def steps

  def builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.getEnv() >> []
    builder = new GradleBuilder(steps, 'test')
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

  def "securityCheck calls 'gradle dependencyCheckAnalyze"() {
    setup:
      def closure
      steps.withAzureKeyvault(_, { closure = it }) >> { closure.call() }

    when:
      builder.securityCheck()
    then:
      1 * steps.sh({
        GString it -> it.startsWith(GRADLE_CMD) && it.contains('dependencyCheckAnalyze') &&
        it.contains('jdbc:postgresql://owaspdependency-v5-prod')
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
                    it.contains("-Dpact.broker.url=${PACT_BROKER_URL} -Dpact.provider.version=${version} -Dpact.verifier.publishResults=${publishResults} runProviderPactVerification")})
  }

  def "runConsumerTests triggers a gradlew hook"() {
    setup:
      def version = "v3r510n"
    when:
      builder.runConsumerTests(PACT_BROKER_URL, version)
    then:
      1 * steps.sh({it.startsWith(GRADLE_CMD) &&
                    it.contains("-Dpact.broker.url=${PACT_BROKER_URL} -Dpact.consumer.version=${version} runAndPublishConsumerPactTests")})
  }
}
