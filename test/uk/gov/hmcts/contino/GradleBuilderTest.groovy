package uk.gov.hmcts.contino

import spock.lang.Specification

class GradleBuilderTest extends Specification {

  static final String GRADLE_CMD = './gradlew'

  def steps

  def builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.getEnv() >> [INFRA_VAULT_NAME: 'blah']
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

  def "securityCheck calls 'gradle dependencyCheckAnalyze'"() {
    when:
      builder.securityCheck()
    then:
      2 * steps.sh(_ as Map) >> ''
      1 * steps.sh({ GString it -> it.startsWith(GRADLE_CMD) && it.contains('dependencyCheckAnalyze') })
  }

}
