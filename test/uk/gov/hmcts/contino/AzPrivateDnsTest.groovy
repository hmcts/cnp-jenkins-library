package uk.gov.hmcts.contino

import spock.lang.Specification

class AzPrivateDnsTest extends Specification {

  static final String ENVIRONMENT = 'sandbox'

  def steps
  def azPrivateDns
  def environmentDnsConfigEntry
  def response = ["content": ["subscriptions":
                          [["name": "DTS-CFTSBOX-INTSVC", "zoneTemplate": 'service.core-compute-${environment}.internal', "ttl": 300, "active": true,
                            "environments": [["name": "sandbox", "ttl": 3600], ["name": "idam-sandbox"]],
                            "resourceGroup": "core-infra-intsvc-rg"],
                           ["name": "DTS-CFTPTL-INTSVC", "zoneTemplate": 'service.core-compute-${environment}.internal', "ttl": 3600, "active": false,
                            "environments": [["name": "prod", "ttl": 2400], ["name": "idam-prod"]],
                            "resourceGroup": "core-infra-intsvc-rg"]]]]


  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.readYaml([text: response.content]) >> response.content
    steps.httpRequest(_) >> response
    steps.error(_) >> { throw new Exception(_ as String) }
    steps.env >> ["SUBSCRIPTION_NAME": "sandbox"]
    environmentDnsConfigEntry = new EnvironmentDnsConfig(steps).getEntry(ENVIRONMENT, 'plum', 'recipes-service')
    azPrivateDns = new AzPrivateDns(steps, ENVIRONMENT, environmentDnsConfigEntry)
  }

  def cleanup() {
    EnvironmentDnsConfig.envDnsConfigMap = null
  }

  def "registerAzDns() should register the record name with the private dns zone for the environment"() {
    def recordName = "rn"
    def ip = "4.3.2.1"

    def zone = "service.core-compute-${ENVIRONMENT}.internal"
    def resourceGroup = "core-infra-intsvc-rg"
    def subscription = "DTS-CFTSBOX-INTSVC"
    def ttl = 3600

    when:
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, ENVIRONMENT, environmentDnsConfigEntry])
      azPrivateDns.registerDns(recordName, ip)

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("network private-dns record-set a create -g ${resourceGroup} -z ${zone} -n ${recordName} --ttl ${ttl} --subscription ${subscription}") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("network private-dns record-set a add-record -g ${resourceGroup} -z ${zone} -n ${recordName} -a ${ip} --subscription ${subscription}") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

  def "registerAzDns() should use environment managed identity profile on environment agent"() {
    given:
    def envAgentSteps = Mock(JenkinsStepMock.class)
    envAgentSteps.readYaml([text: response.content]) >> response.content
    envAgentSteps.httpRequest(_) >> response
    envAgentSteps.error(_) >> { throw new Exception(_ as String) }
    envAgentSteps.env >> ["SUBSCRIPTION_NAME": "nonprod",
                          "BUILD_AGENT_TYPE": "ubuntu-sbox"]
    def envAgentDnsConfigEntry = new EnvironmentDnsConfig(envAgentSteps).getEntry(ENVIRONMENT, 'plum', 'recipes-service')
    def envAgentAzPrivateDns = new AzPrivateDns(envAgentSteps, ENVIRONMENT, envAgentDnsConfigEntry)
    def recordName = "rn"
    def ip = "4.3.2.1"

    when:
      envAgentAzPrivateDns.registerDns(recordName, ip)

    then:
    1 * envAgentSteps.sh({it.get('script') == "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-sbox az login --identity" &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
    1 * envAgentSteps.sh({it.containsKey('script') &&
      it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-sbox az network private-dns record-set a show") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
    1 * envAgentSteps.sh({it.containsKey('script') &&
      it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-sbox az network private-dns record-set a create") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
    1 * envAgentSteps.sh({it.containsKey('script') &&
      it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-sbox az network private-dns record-set a add-record") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

  def "registerAzDns() should throw exception if environment does not have a private DNS zone registered"() {
    def environment = "sbox"
    def recordName = "rn"
    def ip = "4.3.2.1"

    when:
      azPrivateDns = Spy(AzPrivateDns, constructorArgs:[steps, environment, new EnvironmentDnsConfig(steps).getEntry(environment, 'plum', 'recipes-service')])
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
