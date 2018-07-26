package uk.gov.hmcts.contino

import spock.lang.Specification

class GradleBuilderTest extends Specification {

  static final String GRADLE_CMD = './gradlew'

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


  def "securityCheck calls 'gradle dependencyCheckAnalyze'"() {
    setup:
    def closure
    steps.withCredentials(_, { closure = it }) >> { closure.call() }
    steps.metaClass.usernamePassword { LinkedHashMap map ->
      return []
    }

    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ GString it -> it.startsWith(GRADLE_CMD) && it.contains('dependencyCheckAnalyze') })
  }

}
