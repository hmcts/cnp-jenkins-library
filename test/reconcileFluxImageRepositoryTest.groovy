import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class ReconcileFluxImageRepositoryTest extends BasePipelineTest {

  def script
  List<Map> shellCalls = []
  List<Map> environmentAgentCalls = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      BUILD_AGENT_TYPE: 'ubuntu-stg',
      DEPLOYMENT_ENVIRONMENT: 'stg',
      PTL_AKS_RESOURCE_GROUP: 'ptl-rg',
      PTL_AKS_CLUSTER_NAME: 'ptl-aks',
      AKS_PTL_SUBSCRIPTION_NAME: 'ptl-sub'
    ])
    helper.registerAllowedMethod('libraryResource', [String.class], { 'script-content' })
    helper.registerAllowedMethod('writeFile', [Map.class], {})
    helper.registerAllowedMethod('sh', [String.class], { String script ->
      shellCalls << [script: script]
      ''
    })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellCalls << args
      ''
    })
    helper.registerAllowedMethod('withEnvironmentAgent', [String.class, String.class, Closure.class], { String environment, String product, Closure body ->
      environmentAgentCalls << [environment: environment, product: product]
      binding.env.BUILD_AGENT_TYPE = "ubuntu-${environment}"
      binding.env.DEPLOYMENT_ENVIRONMENT = environment
      body()
    })
    script = loadScript('vars/reconcileFluxImageRepository.groovy')
  }

  @Test
  void 'uses current environment config when no target environment is supplied'() {
    when:
      script.call(product: 'toffee', component: 'frontend')

    then:
      assertThat(environmentAgentCalls).isEmpty()
      assertThat(shellCalls[0].script).contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-stg')
      assertThat(shellCalls[0].script).contains('az aks get-credentials --resource-group ptl-rg --name ptl-aks --subscription ptl-sub')
  }

  @Test
  void 'switches to requested environment agent before reconcile'() {
    when:
      script.call(product: 'toffee', component: 'frontend', environment: 'ptl')

    then:
      assertThat(environmentAgentCalls).containsExactly([environment: 'ptl', product: 'toffee'])
      assertThat(shellCalls[0].script).contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-ptl')
  }
}
