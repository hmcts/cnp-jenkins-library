package uk.gov.hmcts.contino

import spock.lang.Specification

class SubscriptionTest extends Specification {

  def "constructor should throw an exception when intialized with null input"() {
    when:
    new Subscription(null)

    then:
    thrown(NullPointerException)
  }

  def "Defaults to 'prod' for prod env when prod subscription var override not set"() {
    when:
    def subscription = new Subscription(["unusedVar": "unused"])

    then:
    assert subscription.prodName == "prod"
  }

  def "Defaults to 'nonprod' for nonprod env when nonProd subscription var override not set"() {
    when:
    def subscription = new Subscription(["unusedVar": "unused"])

    then:
    assert subscription.nonProdName == "nonprod"
  }
  
  def "Defaults to 'hmctsdemo' for hmctsdemo env when hmctsDemo subscription var override not set"() {
    when:
    def subscription = new Subscription(["unusedVar": "unused"])

    then:
    assert subscription.hmctsDemoName == "hmctsdemo"
  }

  def "Overrides prod env name when subscription var override is set"() {
    when:
    def subscription = new Subscription(["PROD_SUBSCRIPTION_NAME": "sprod"])

    then:
    assert subscription.prodName == "sprod"
  }

  def "Overrides nonProd env name when subscription var override is set"() {
    when:
    def subscription = new Subscription(["NONPROD_SUBSCRIPTION_NAME": "snonprod"])

    then:
    assert subscription.nonProdName == "snonprod"
  }

}
