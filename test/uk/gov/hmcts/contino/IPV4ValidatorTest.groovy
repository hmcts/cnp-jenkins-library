package uk.gov.hmcts.contino

import spock.lang.Specification

class IPV4ValidatorTest extends Specification {

  def "validate passes with private ip address"() {
    when:
      def result = IPV4Validator.validate("10.0.0.1")
    then:
      result
  }

  def "validate passes with public ip address"() {
    when:
      def result = IPV4Validator.validate("212.19.200.2")
    then:
      result
  }

  def "validate fails with empty string"() {
    when:
      def result = IPV4Validator.validate("")
    then:
      !result
  }

  def "validate fails with null"() {
    when:
      def result = IPV4Validator.validate(null)
    then:
      !result
  }
}
