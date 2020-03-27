package uk.gov.hmcts.contino

import spock.lang.Specification

class AzPrivateDnsTest extends Specification {

  static final String ENVIRONMENT = 'sandbox'

  def steps
  def azPrivateDns

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> ["SUBSCRIPTION_NAME": "sandbox"]
    azPrivateDns = new AzPrivateDns(steps, ENVIRONMENT)
  }

  def "registerAzDns() should register the record name with the private dns zone for the environment"() {
    def recordName = "rn"
    def ip = "4.3.2.1"

    def zone = "service.core-compute-${ENVIRONMENT}.internal"
    def resourceGroup = "mgmt-intdns-sboxintsvc"
    def subscriptionId = "b3394340-6c9f-44ca-aa3e-9ff38bd1f9ac"
    def ttl = 300

    when:
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, ENVIRONMENT])
      azPrivateDns.getAccessToken() >> "some_access_token"
      azPrivateDns.registerAzDns(recordName, ip)

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("network private-dns record-set a create -g ${resourceGroup} -z ${zone} -n ${recordName} --ttl ${ttl} --subscription ${subscriptionId}") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("network private-dns record-set a add-record -g ${resourceGroup} -z ${zone} -n ${recordName} -a ${ip} --subscription ${subscriptionId}") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

  def "registerAzDns() should throw exception if environment does not have a private DNS zone registered"() {
    def environment = "sbox"
    def recordName = "rn"
    def ip = "4.3.2.1"

    when:
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, environment])
      azPrivateDns.registerAzDns(recordName, ip)

    then:
      thrown RuntimeException
  }

  def "registerAzDns() should throw exception if invalid ip address registered"() {
    def recordName = "rn"
    def ip = "4.3.2.256"

    when:
      azPrivateDns.registerAzDns(recordName, ip)

    then:
      thrown RuntimeException
  }

}
