package uk.gov.hmcts.contino

import spock.lang.Specification

class YarnBuilderTest extends Specification {
  static final String YARN_CMD = 'yarn'
  static final String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"

  def steps

  def builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    builder = new YarnBuilder(steps)
  }

  def "build calls 'yarn install' and 'yarn lint'"() {
    when:
      builder.build()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('lint') })
  }

  def "test calls 'yarn test' and 'yarn test:coverage' and 'yarn test:a11y'"() {
    when:
    builder.test()
    then:
    1 * steps.sh(['script': 'yarn check &> /dev/null', 'returnStatus': true])
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
    1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:coverage') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:a11y') })
  }

  def "sonarScan calls 'yarn sonar-scan'"() {
    when:
      builder.sonarScan()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('sonar-scan') })
  }

  def "smokeTest calls 'yarn test:smoke'"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:smoke') })
  }

  def "functionalTest calls 'yarn test:functional'"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:functional') })
  }

  def "apiGatewayTest calls 'yarn test:apiGateway'"() {
    when:
      builder.apiGatewayTest()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:apiGateway') })
  }

  def "crossBrowserTest calls 'yarn test:crossbrowser'"() {
    when:
    builder.crossBrowserTest()
    then:
    1 * steps.withSauceConnect({ it.startsWith('reform_tunnel') }, _ as Closure)
    when:
    builder.yarn("test:crossbrowser")
    then:
    1 * steps.sh(['script': 'yarn check &> /dev/null', 'returnStatus': true])
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
    1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:crossbrowser') })
  }

  def "mutationTest calls 'yarn test:mutation'"() {
    when:
        builder.mutationTest()
    then:
        1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
        1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
        1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
        1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:mutation') })
  }

  /*def "securityCheck calls 'yarn test:nsp'"() {
    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ GString it -> it.startsWith(YARN_CMD) && it.contains('test:nsp') })
  }*/

  def "full functional tests calls 'yarn test:fullfunctional'"() {
    when:
        builder.fullFunctionalTest()
    then:
        1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
        1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
        1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
        1*steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:fullfunctional') })
  }

  def "runProviderVerification triggers a yarn hook"() {
    when:
      builder.runProviderVerification("${PACT_BROKER_URL}")
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL} yarn test:pact-verify") })
  }

  def "runConsumerTests triggers a yarn hook"() {
    when:
      builder.runConsumerTests("${PACT_BROKER_URL}")
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL} yarn test:pact") })
  }


}
