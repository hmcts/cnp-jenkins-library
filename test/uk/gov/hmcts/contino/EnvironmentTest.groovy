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

  def "Defaults to 'prodv2' for prod env when environment suffix is provided"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"])

    then:
    assert environment.prodName == "prodv2"
  }

  def "Defaults to 'aat' for nonprod env when nonProd environment var override not set"() {
    when:
    def environment = new Environment(["unusedVar": "unused"])

    then:
    assert environment.nonProdName == "aat"
  }

  def "Defaults to 'aatv2' for nonprod env when environment suffix is provided"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"])

    then:
    assert environment.nonProdName == "aatv2"
  }

  def "Overrides prod env name when environment var override is set"() {
    when:
    def environment = new Environment(["PROD_ENVIRONMENT_NAME": "sprod"])

    then:
    assert environment.prodName == "sprod"
  }

  def "Overrides prod env name with suffix when environment var override is set"() {
    when:
    def environment = new Environment(["PROD_ENVIRONMENT_NAME": "sprod", "ENV_SUFFIX": "v2"])

    then:
    assert environment.prodName == "sprodv2"
  }

  def "Overrides nonProd env name when environment var override is set"() {
    when:
    def environment = new Environment(["NONPROD_ENVIRONMENT_NAME": "saat"])

    then:
    assert environment.nonProdName == "saat"
  }

  def "Overrides nonProd env name when when suffix if defined"() {
    when:
    def environment = new Environment(["NONPROD_ENVIRONMENT_NAME": "saat",
                                       "ENV_SUFFIX": "v2"])

    then:
    assert environment.nonProdName == "saatv2"
  }

  def "environment suffix is applied to default demo environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"])

    then:
    assert environment.demoName == "demov2"
  }

  def "environment suffix is applied to default preview environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"])

    then:
    assert environment.previewName == "previewv2"
  }

  def "default hmctsdemo environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused"])

    then:
    assert environment.hmctsDemoName == "hmctsdemo"
  }

  def "overrride hmctsdemo environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "HMCTSDEMO_ENVIRONMENT_NAME": "hmctsdemooverride"])

    then:
    assert environment.hmctsDemoName == "hmctsdemooverride"
  }

  def "overrride hmctsdemo environment with suffix"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "HMCTSDEMO_ENVIRONMENT_NAME": "hmctsdemooverride", "ENV_SUFFIX": "v2"])

    then:
    assert environment.hmctsDemoName == "hmctsdemooverridev2"
  }

  def "environment suffix is applied to default hmctsdemo environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"])

    then:
    assert environment.hmctsDemoName == "hmctsdemov2"
  }

  def "environment suffix is EXCLUDED from hmctsdemo environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"], false)

    then:
    assert environment.hmctsDemoName == "hmctsdemo"
  }

  def "environment suffix is EXCLUDED to default nonprod environment"() {
    when:
    def environment = new Environment(["unusedVar": "unused", "ENV_SUFFIX": "v2"], false)

    then:
    assert environment.nonProdName == "aat"
  }

  def "environment suffix is NOT applied to default nonprod environment when includeSuffix is false"() {
    when:
    def environment = new Environment(["unusedVar": "unused"], false)

    then:
    assert environment.nonProdName == "aat"
  }
}
