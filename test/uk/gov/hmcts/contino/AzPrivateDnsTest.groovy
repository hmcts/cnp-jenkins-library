package uk.gov.hmcts.contino

import groovy.json.JsonOutput
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
    def ttl = "300"

    def expectedUrl = "https://management.azure.com/subscriptions/b3394340-6c9f-44ca-aa3e-9ff38bd1f9ac/resourceGroups/mgmt-intdns-sboxintsvc/providers/Microsoft.Network/privateDnsZones/service.core-compute-sandbox.internal/A/${recordName}?api-version=2018-09-01"
    def expectedBody = JsonOutput.toJson(
      [
        "properties": [
          "ttl": "${ttl}",
          "aRecords": [["ipv4Address": "${ip}"]]
        ],
      ])

    when:
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, ENVIRONMENT])
      azPrivateDns.getAccessToken() >> "some_access_token"
      azPrivateDns.registerAzDns(recordName, ip)

    then:
      1 * steps.httpRequest({it.get('httpMode').equals('PUT') &&
        it.get('acceptType').equals('APPLICATION_JSON') &&
        it.get('contentType').equals('APPLICATION_JSON') &&
        it.get('url').equals("${expectedUrl}") &&
        it.get('requestBody').equals("${expectedBody}") &&
        it.get('consoleLogResponseBody').equals(true) &&
        it.get('validResponseCodes').equals('200:201')})
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
