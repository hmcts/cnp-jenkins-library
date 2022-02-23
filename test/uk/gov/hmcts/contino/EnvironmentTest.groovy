package uk.gov.hmcts.contino

import spock.lang.Specification

class EnvironmentTest extends Specification {

  def "constructor should throw an exception when intialized with null input"() {
    when:
    new Environment(null)

    then:
    thrown(NullPointerException)
  }

  def "Defaults to 'prod' for prod env when prod environment var override not set"() {
    when:
    def environment = new Environment(["unusedVar": "unused"])

    then:
    assert environment.prodName == "prod"
  }

  def "Defaults to 'aat' for nonprod env when nonProd environment var override not set"() {
    when:
    def environment = new Environment(["unusedVar": "unused"])

    then:
    assert environment.nonProdName == "aat"
  }


  def "Overrides prod env name when environment var override is set"() {
    when:
    def environment = new Environment(["PROD_ENVIRONMENT_NAME": "sprod"])

    then:
    assert environment.prodName == "sprod"
  }

  def "Overrides nonProd env name when environment var override is set"() {
    when:
    def environment = new Environment(["NONPROD_ENVIRONMENT_NAME": "saat"])

    then:
    assert environment.nonProdName == "saat"
  }

  def "Converts prod to production"() {
    when:
    def environmentTagName = Environment.toTagName("prod")

    then:
    assert environmentTagName == "production"
  }

  def "Converts saat to saat"() {
    when:
    def environmentTagName = Environment.toTagName("saat")

    then:
    assert environmentTagName == "saat"
  }

  def "Throws exception on unknown environment"() {
    when:
    new Environment("unknown_environment")

    then:
    thrown(RuntimeException)
  }

}
