package uk.gov.hmcts.contino

import spock.lang.Specification

class GradleBuilderTest extends Specification {

  def steps = Mock(JenkinsStepMock.class)
  def builder = new GradleBuilder(steps, 'test')

  def "build calls './gradlew assemble'"() {
    when:
      builder.build()
    then:
      1 * steps.sh('./gradlew assemble')
  }

  def "test calls './gradlew --info check"() {
    when:
      builder.test()
    then:
      1 * steps.sh('./gradlew --info check')
  }

  def "smokeTest calls './gradlew --info --rerurn-tasks smoke'"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh('./gradlew --info --rerun-tasks smoke')
  }
}
