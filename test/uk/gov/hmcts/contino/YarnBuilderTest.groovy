package uk.gov.hmcts.contino

import spock.lang.Specification

class YarnBuilderTest extends Specification {
  static final String YARN_CMD = 'yarn'

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
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('install') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('lint') })
  }

  def "test calls 'yarn test' and 'yarn test:coverage' and 'yarn test:a11y'"() {
    when:
      builder.test()
    then:
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:coverage') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:a11y') })
  }

  def "sonarScan calls 'yarn sonar-scan'"() {
    when:
      builder.sonarScan()
    then:
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('sonar-scan') })
  }

  def "smokeTest calls 'yarn test:smoke'"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:smoke') })
  }

  def "functionalTest calls 'yarn test:functional'"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:functional') })
  }

  def "apiGatewayTest calls 'yarn test:apiGateway'"() {
    when:
    builder.apiGatewayTest()
    then:
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:apiGateway') })
  }

  def "crossBrowserTest calls 'yarn test:crossbrowser'"() {
    when:
    builder.crossBrowserTest()
    then:
    1 * steps.withSauceConnect({it.startsWith('reform_tunnel')})
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:crossbrowser') })
  }

  def "securityCheck calls 'yarn test:nsp'"() {
    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ GString it -> it.startsWith(YARN_CMD) && it.contains('test:nsp') })
  }


}
