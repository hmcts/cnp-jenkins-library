package uk.gov.hmcts.contino

import spock.lang.Specification

class EnvironmentTest extends Specification {

  def "constructor should throw an exception when intialized with null input"() {
    when:
    new Environment(null)

    then:
    thrown(NullPointerException)
  }

  def "isProduction should return true when environment is 'prod'"() {
    when:
    def environment = new Environment('prod')

    then:
    environment.isProduction()
  }

  def "isProduction should return true when environment is 'sprod'"() {
    when:
    def environment = new Environment('sprod')

    then:
    environment.isProduction()
  }

  def "isProduction should return false when environment is not 'prod' or 'sprod'"() {
    when:
    def environment = new Environment('nonprod')

    then:
    !environment.isProduction()
  }

  def "isAATEnvironment should return true when environment is 'aat'"() {
    when:
    def environment = new Environment('aat')

    then:
    environment.isAATEnvironment()
  }

  def "isAATEnvironment should return true when environment is  'saat'"() {
    when:
    def environment = new Environment('saat')

    then:
    environment.isAATEnvironment()
  }

  def "isAATEnvironment should return false when environment is neither 'aat' or 'saat'"() {
    when:
    def environment = new Environment('nonprod')

    then:
    !environment.isAATEnvironment()
  }
}
