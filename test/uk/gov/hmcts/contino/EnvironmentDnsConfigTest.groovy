package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class EnvironmentDnsConfigTest extends Specification {

  static final String ENVIRONMENT = 'sandbox'

  def steps
  def environmentDnsConfig
  static def response = ["content": ["subscriptions":
                          [["name": "DTS-CFTSBOX-INTSVC", "zoneTemplate": 'service.core-compute-${environment}.internal',
                            "ttl": 300, "active": true, "consulActive": true,
                            "environments": [["name": "sandbox", "ttl": 3600], ["name": "idam-sandbox", "consulActive": false]],
                            "id": "1497c3d7-ab6d-4bb7-8a10-b51d03189ee3",
                            "resourceGroup": "core-infra-intsvc-rg"],
                           ["name": "DTS-CFTPTL-INTSVC", "zoneTemplate": 'service.core-compute-${environment}.internal',
                            "ttl": 3600, "active": false, "consulActive": true,
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
  }

  def "getEntry() should return a correctly configured entry for a known environment"() {
    def environment = 'idam-sandbox'

    when:
    def idamSandbox = environmentDnsConfig.getEntry(environment)

    then:
    assertThat(idamSandbox.environment).isEqualTo(environment)
    assertThat(idamSandbox.consulActive).isFalse()
    assertThat(idamSandbox.active).isTrue()
    assertThat(idamSandbox.subscription).isEqualTo("DTS-CFTSBOX-INTSVC")
    assertThat(idamSandbox.subscriptionId).isEqualTo("1497c3d7-ab6d-4bb7-8a10-b51d03189ee3")
    assertThat(idamSandbox.resourceGroup).isEqualTo("core-infra-intsvc-rg")
    assertThat(idamSandbox.ttl).isEqualTo(300)
    assertThat(idamSandbox.zone).isEqualTo("service.core-compute-idam-sandbox.internal")
  }

  def "getEntry() should return null for an unknown environment"() {
    def environment = 'idam-sbox'

    when:
    def idamSbox = environmentDnsConfig.getEntry(environment)

    then:
    assertThat(idamSbox).isNull()
  }

}
