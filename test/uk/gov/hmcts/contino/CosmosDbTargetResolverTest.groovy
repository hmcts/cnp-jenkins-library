package uk.gov.hmcts.contino

import spock.lang.Specification

class CosmosDbTargetResolverTest extends Specification {

  def "returns database configured by the Jenkins installation"() {
    given:
    def steps = Mock(JenkinsStepMock) {
      getEnv() >> [(CosmosDbTargetResolver.DATABASE_ENV_VAR): "sds-jenkins"]
    }
    def resolver = new CosmosDbTargetResolver(steps)

    expect:
    resolver.databaseName() == "sds-jenkins"
  }

  def "returns default database when the installation variable is absent"() {
    given:
    def steps = Mock(JenkinsStepMock) {
      getEnv() >> [:]
    }
    def resolver = new CosmosDbTargetResolver(steps)

    expect:
    resolver.databaseName() == "jenkins"
  }

  def "returns default database when the installation variable is blank"() {
    given:
    def steps = Mock(JenkinsStepMock) {
      getEnv() >> [(CosmosDbTargetResolver.DATABASE_ENV_VAR): "  "]
    }
    def resolver = new CosmosDbTargetResolver(steps)

    expect:
    resolver.databaseName() == "jenkins"
  }
}
