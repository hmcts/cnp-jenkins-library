package uk.gov.hmcts.contino

import spock.lang.Specification

class CosmosDbTargetResolverTest extends Specification {

  def steps = Mock(JenkinsStepMock) {
    echo(_) >> {}
  }

  def "returns SDS database when topics include jenkins-sds"() {
    given:
    def resolver = new CosmosDbTargetResolver(steps) {
      @Override
      protected String fetchTopicsText() {
        return "names:[jenkins-sds,java]"
      }
    }

    expect:
    resolver.databaseName() == "sds-jenkins"
  }

  def "returns default database when topics do not include sds"() {
    given:
    def resolver = new CosmosDbTargetResolver(steps) {
      @Override
      protected String fetchTopicsText() {
        return "names:[jenkins-cft,platform]"
      }
    }

    expect:
    resolver.databaseName() == "jenkins"
  }

  def "falls back to default when fetch fails"() {
    given:
    def resolver = new CosmosDbTargetResolver(steps) {
      @Override
      protected String fetchTopicsText() {
        throw new RuntimeException("boom")
      }
    }

    expect:
    resolver.databaseName() == "jenkins"
  }
}
