package uk.gov.hmcts.contino

import spock.lang.Specification

class AKSSubscriptionTest extends Specification {

  def "constructor should throw an exception when intialized with null input"() {
    when:
    new AKSSubscription(null)

    then:
    thrown(NullPointerException)
  }

  def "Defaults to 'DCD-CNP-DEV' for preview env when preview aksSubscription var override not set"() {
    when:
    def aksSubscription = new AKSSubscription(["unusedVar": "unused"])

    then:
    assert aksSubscription.previewName == "DCD-CNP-DEV"
  }

  def "Defaults to 'DCD-CNP-DEV' for aat env when aat aksSubscription var override not set"() {
    when:
    def aksSubscription = new AKSSubscription(["unusedVar": "unused"])

    then:
    assert aksSubscription.aatName == "DCD-CNP-DEV"
  }
  
  def "Overrides preview env name when aksSubscription var override is set"() {
    when:
    def aksSubscription = new AKSSubscription(["AKS_PREVIEW_SUBSCRIPTION_NAME": "DCD-CFTAPPS-SBOX"])

    then:
    assert aksSubscription.previewName == "DCD-CFTAPPS-SBOX"
  }

  def "Overrides aat env name when aksSubscription var override is set"() {
    when:
    def aksSubscription = new AKSSubscription(["AKS_AAT_SUBSCRIPTION_NAME": "DCD-CFTAPPS-SBOX"])

    then:
    assert aksSubscription.aatName == "DCD-CFTAPPS-SBOX"
  }

}
