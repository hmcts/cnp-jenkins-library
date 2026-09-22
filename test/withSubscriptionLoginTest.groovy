import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class WithSubscriptionLoginTest extends BasePipelineTest {

  def script
  List<Map> environmentAgentCalls = []
  List<String> shellScripts = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      BUILD_AGENT_TYPE: 'ubuntu-stg',
      DEPLOYMENT_ENVIRONMENT: 'stg',
      PRODUCT: 'toffee'
    ])

    helper.registerAllowedMethod('echo', [String.class], { String ignored -> })
    helper.registerAllowedMethod('fileExists', [String.class], { String ignored -> false })
    helper.registerAllowedMethod('withAzureKeyvault', [List.class, Closure.class], { List ignored, Closure body ->
      binding.env.ARM_SUBSCRIPTION_ID = 'subscription-id'
      body()
    })
    helper.registerAllowedMethod('withEnvironmentAgent', [String.class, String.class, String.class, Closure.class], {
      String environment, String product, String agentLabel, Closure body ->
        environmentAgentCalls << [environment: environment, product: product, agentLabel: agentLabel]

        def previousBuildAgentType = binding.env.BUILD_AGENT_TYPE
        def previousDeploymentEnvironment = binding.env.DEPLOYMENT_ENVIRONMENT
        binding.env.BUILD_AGENT_TYPE = agentLabel
        binding.env.DEPLOYMENT_ENVIRONMENT = environment
        try {
          body()
        } finally {
          binding.env.BUILD_AGENT_TYPE = previousBuildAgentType
          binding.env.DEPLOYMENT_ENVIRONMENT = previousDeploymentEnvironment
        }
    })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellScripts << args.script
      if (args.script.contains('account show --query tenantId')) {
        return 'tenant-id'
      }
      return ''
    })

    script = loadScript('vars/withSubscriptionLogin.groovy')
  }

  @Test
  void 'env-like subscription login hops to matching environment agent'() {
    boolean bodyCalled = false

    script.call('dev') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(environmentAgentCalls).containsExactly([
      environment: 'dev',
      product: 'toffee',
      agentLabel: 'ubuntu-dev'
    ])
    assertThat(shellScripts).anyMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-dev az login --identity') }
    assertThat(binding.env.SUBSCRIPTION_NAME).isEqualTo('dev')
  }

  @Test
  void 'env-like subscription login does not hop when already on matching agent'() {
    binding.env.BUILD_AGENT_TYPE = 'ubuntu-stg'
    boolean bodyCalled = false

    script.call('stg') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(environmentAgentCalls).isEmpty()
    assertThat(shellScripts).anyMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-stg az login --identity') }
  }

  @Test
  void 'CFT-style subscription login keeps existing direct behaviour'() {
    boolean bodyCalled = false

    script.call('nonprod') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(environmentAgentCalls).isEmpty()
    assertThat(shellScripts).anyMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-nonprod az login --identity') }
  }

  @Test
  void 'nightly pipeline login does not hop on env-like subscriptions'() {
    binding.env.BUILD_AGENT_TYPE = 'daily'
    binding.env.IS_NIGHTLY_PIPELINE = 'true'
    boolean bodyCalled = false

    script.call('stg') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(environmentAgentCalls).isEmpty()
    assertThat(shellScripts).anyMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-stg az login --identity') }
  }
}