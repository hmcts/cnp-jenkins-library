package uk.gov.hmcts.contino

import spock.lang.Specification

class AzPrivateDnsTest extends Specification {

  static final String ENVIRONMENT = 'sandbox'

  def steps
  def azPrivateDns
  def environmentDnsConfig
  static def response = ["content": ["subscriptions":
                          [["name": "DTS-CFTSBOX-INTSVC", "zoneTemplate": 'service.core-compute-${environment}.internal', "ttl": 300, "active": true,
                            "environments": [["name": "sandbox", "ttl": 3600], ["name": "idam-sandbox"]],
                            "id": "1497c3d7-ab6d-4bb7-8a10-b51d03189ee3",
                            "resourceGroup": "core-infra-intsvc-rg"],
                           ["name": "DTS-CFTPTL-INTSVC", "zoneTemplate": 'service.core-compute-${environment}.internal', "ttl": 3600, "active": false,
                            "environments": [["name": "prod", "ttl": 2400], ["name": "idam-prod"]],
                            "id": "1baf5470-1c3e-40d3-a6f7-74bfbce4b348",
                            "resourceGroup": "core-infra-intsvc-rg"]]]]


  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.readYaml([text: response.content]) >> response.content
    steps.httpRequest(_) >> response
    steps.error(_) >> { throw new Exception(_ as String) }
    steps.env >> ["SUBSCRIPTION_NAME": "sandbox"]
    environmentDnsConfig = new EnvironmentDnsConfig(steps)
    azPrivateDns = new AzPrivateDns(steps, ENVIRONMENT, environmentDnsConfig)
  }

  def "registerAzDns() should register the record name with the private dns zone for the environment"() {
    def recordName = "rn"
    def ip = "4.3.2.1"

    def zone = "service.core-compute-${ENVIRONMENT}.internal"
    def resourceGroup = "core-infra-intsvc-rg"
    def subscriptionId = "1497c3d7-ab6d-4bb7-8a10-b51d03189ee3"
    def ttl = 3600

    when:
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, ENVIRONMENT, environmentDnsConfig])
      azPrivateDns.registerDns(recordName, ip)

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
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, environment, environmentDnsConfig])
      azPrivateDns.registerDns(recordName, ip)

    then:
      thrown RuntimeException
  }

  def "registerAzDns() should throw exception if invalid ip address registered"() {
    def recordName = "rn"
    def ip = "4.3.2.256"

    when:
      azPrivateDns.registerDns(recordName, ip)

    then:
      thrown RuntimeException
  }

}
