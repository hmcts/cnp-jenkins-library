import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class StartEnvironmentIfRequiredTest extends BasePipelineTest {

  def script
  List<Map> shellCalls = []
  List<String> infoLogs = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      BUSINESS_AREA_TAG: 'CFT',
      AKS_RESOURCE_GROUP: 'cft-sbox-rg',
      AKS_CLUSTER_NAME: 'cft-sbox-00-aks'
    ])
    binding.setVariable('log', [
      info: { String message -> infoLogs << message }
    ])

    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellCalls << args
      String command = args.script
      if (command.contains('aks show')) {
        return '{"powerState":{"code":"Running"}}'
      }
      return ''
    })

    script = loadScript('vars/startEnvironmentIfRequired.groovy')
  }

  @Test
  void 'sandbox AKS start check uses an isolated AKS azure config'() {
    script.call([
      environment: 'sandbox',
      subscription: 'sandbox',
      aksSubscription: [name: 'DCD-CFTAPPS-SBOX']
    ])

    def shellScripts = shellCalls*.script.findAll { it }

    assertThat(infoLogs).contains('Checking AKS Environment: sandbox, Subscription is DCD-CFTAPPS-SBOX, in business area: CFT')
    assert shellScripts.every { it?.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-sandbox-aks') }
    assert shellScripts.any { it?.contains('az login --identity') }
    assert shellScripts.any { it?.contains('az account set -s DCD-CFTAPPS-SBOX') }
    // The trailing space distinguishes the base config dir from .azure-sandbox-aks.
    assert !shellScripts.any { it?.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-sandbox ') }
    assert !shellScripts.any { it?.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins') }
  }
}
