package uk.gov.hmcts.contino

import spock.lang.Specification

class GradleBuilderTest extends Specification {

  static final String GRADLE_CMD = './gradlew'

  def steps = Mock(JenkinsStepMock.class)
  def builder = new GradleBuilder(steps, 'test')

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

  def "smokeTest calls 'gradle --rerurn-tasks smoke' with '--rerun-tasks' flag"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh({ it.startsWith(GRADLE_CMD) && it.contains('smoke') && it.contains('--rerun-tasks') })
  }
}
