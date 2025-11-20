package uk.gov.hmcts.contino

import spock.lang.Specification

class NextJsBuilderTest extends Specification {

  def steps
  YarnBuilder yarnBuilder
  NextJsBuilder builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.fileExists(_ as String) >> false
    steps.readJSON(_ as Map) >> [scripts: [:]]
    steps.getEnv() >> [
      BRANCH_NAME: 'master',
    ]
    def sampleCVEReport = new File(this.getClass().getClassLoader().getResource('yarn-audit-report-no-issues.txt').toURI()).text
    steps.readFile(_ as String) >> sampleCVEReport
    def closure
    steps.withCredentials(_, { it.call() }) >> { closure = it }
    steps.usernamePassword(_ as Map) >> [:]
    
    builder = new NextJsBuilder(steps)
    yarnBuilder = builder.builder
  }

  def "build calls yarn lint, addVersionInfo and yarn build"() {
    when:
      builder.build()
    then:
      2 * steps.sh({
        it instanceof Map &&
        it.script.contains('yarn install') &&
        it.returnStatus == true
      })
      2 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({
        it instanceof Map &&
        it.script.contains('yarn lint') &&
        it.returnStatus == true
      })
      1 * steps.sh({
        it instanceof Map &&
        it.script.contains('yarn build') &&
        it.returnStatus == true
      })
      1 * steps.sh({ it.contains('tee version') })
  }

  def "test delegates to YarnBuilder"() {
    when:
      builder.test()
    then:
      1 * steps.sh({ it instanceof Map && it.script.contains('yarn test') && it.returnStatus == true })
      1 * steps.sh({ it instanceof Map && it.script.contains('yarn test:coverage') && it.returnStatus == true })
      1 * steps.sh({ it instanceof Map && it.script.contains('yarn test:a11y') && it.returnStatus == true })
  }

  def "smokeTest delegates to YarnBuilder"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh({ it instanceof Map && it.script.contains('yarn test:smoke') && it.returnStatus == true })
  }

  def "functionalTest delegates to YarnBuilder"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh({ it instanceof Map && it.script.contains('yarn test:functional') && it.returnStatus == true })
  }

  def "securityCheck delegates to YarnBuilder"() {
    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ it.contains('./yarn-audit-with-suppressions.sh') })
  }
}
