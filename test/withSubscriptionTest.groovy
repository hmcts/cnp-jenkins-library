import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class WithSubscriptionTest extends BasePipelineTest {

  def script
  List<Map> environmentAgentCalls = []
  List<Map> shellCalls = []
  List<Map> subscriptionLoginCalls = []
  List<String> withEnvVariables = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      INFRA_VAULT_NAME: 'ptl',
      ARM_SUBSCRIPTION_ID: 'target-subscription-id',
      JENKINS_SUBSCRIPTION_ID: 'jenkins-subscription-id'
    ])
    binding.setVariable('log', [
      info: { String ignored -> },
      warning: { String ignored -> }
    ])

    helper.registerAllowedMethod('ansiColor', [String.class, Closure.class], { String ignored, Closure body -> body() })
    helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { List variables, Closure body ->
      withEnvVariables.addAll(variables.collect { it.toString() })
      body()
    })
    helper.registerAllowedMethod('withSubscriptionLogin', [String.class, Closure.class], { String subscription, Closure body ->
      subscriptionLoginCalls << [
        subscription: subscription,
        buildAgentType: binding.env.BUILD_AGENT_TYPE,
        deploymentEnvironment: binding.env.DEPLOYMENT_ENVIRONMENT
      ]
      body()
    })
    helper.registerAllowedMethod('withEnvironmentAgent', [String.class, String.class, Closure.class], { String environment, String product, Closure body ->
      environmentAgentCalls << [environment: environment, product: product]

      def previousBuildAgentType = binding.env.BUILD_AGENT_TYPE
      def previousDeploymentEnvironment = binding.env.DEPLOYMENT_ENVIRONMENT
      binding.env.BUILD_AGENT_TYPE = "ubuntu-${environment}".toString()
      binding.env.DEPLOYMENT_ENVIRONMENT = environment
      try {
        body()
      } finally {
        binding.env.BUILD_AGENT_TYPE = previousBuildAgentType
        binding.env.DEPLOYMENT_ENVIRONMENT = previousDeploymentEnvironment
      }
    })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellCalls << args
      String command = args.script
      if (command.contains('account show --query id')) {
        return 'management-subscription-id'
      }
      if (command.contains('identity show')) {
        return 'jenkins-object-id'
      }
      if (command.contains('storage account keys list')) {
        return 'storage-account-key'
      }
      if (command.contains('account show --query tenantId')) {
        return 'tenant-id'
      }
      return ''
    })

    script = loadScript('vars/withSubscription.groovy')
  }

  @Test
  void 'two arg compatibility flow does not switch environment agents'() {
    boolean bodyCalled = false

    script.call('sandbox') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(environmentAgentCalls).isEmpty()
    assertThat(subscriptionLoginCalls).containsExactly([
      subscription: 'sandbox',
      buildAgentType: null,
      deploymentEnvironment: null
    ])
    assertThat(shellCalls*.script).anyMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins') }
  }

  @Test
  void 'product and environment flow switches to target env then ptl and exposes terraform vars'() {
    boolean bodyCalled = false

    script.call('prod', 'toffee', 'prod') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(subscriptionLoginCalls).containsExactly([
      subscription: 'prod',
      buildAgentType: 'ubuntu-prod',
      deploymentEnvironment: 'prod'
    ])
    assertThat(environmentAgentCalls).containsExactly(
      [environment: 'prod', product: 'toffee'],
      [environment: 'ptl', product: 'toffee']
    )
    assertThat(shellCalls*.script).anyMatch {
      it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-prod') &&
        it.contains('storage account keys list') &&
        it.contains('--resource-group mgmt-state-store-prod')
    }
    assertThat(shellCalls*.script).anyMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-ptl') && it.contains('identity show') }
    assertThat(withEnvVariables).contains(
      'TF_VAR_mgmt_subscription_id=management-subscription-id',
      'TF_VAR_jenkins_AAD_objectId=jenkins-object-id',
      'ARM_ACCESS_KEY=storage-account-key'
    )
  }

  @Test
  void 'sandbox product flow stays on sandbox agent and does not require ptl'() {
    boolean bodyCalled = false
    binding.env.JENKINS_AAD_OBJECT_ID = 'jenkins-aad-object-id'

    script.call('sbox', 'plum', 'sbox') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(subscriptionLoginCalls).containsExactly([
      subscription: 'sbox',
      buildAgentType: 'ubuntu-sbox',
      deploymentEnvironment: 'sbox'
    ])
    assertThat(environmentAgentCalls).containsExactly([environment: 'sbox', product: 'plum'])
    assertThat(shellCalls*.script).noneMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-ptl') }
    assertThat(shellCalls*.script).noneMatch { it.contains('account set --subscription') }
    assertThat(shellCalls*.script).noneMatch { it.contains('identity show') }
    assertThat(shellCalls*.script).noneMatch { it.contains('account show --query id') }
    assertThat(shellCalls*.script).anyMatch {
      it.contains('storage account keys list') &&
        it.contains('--resource-group mgmt-state-store-sbox')
    }
    assertThat(withEnvVariables).contains(
      'TF_VAR_mgmt_subscription_id=jenkins-subscription-id',
      'TF_VAR_jenkins_AAD_objectId=jenkins-aad-object-id'
    )
  }

  @Test
  void 'sandbox product flow uses empty terraform vars when central Jenkins env vars are missing'() {
    binding.env.remove('JENKINS_SUBSCRIPTION_ID')
    binding.env.remove('JENKINS_AAD_OBJECT_ID')

    script.call('sbox', 'plum', 'sbox') {}

    assertThat(shellCalls*.script).noneMatch { it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-ptl') }
    assertThat(shellCalls*.script).noneMatch { it.contains('identity show') }
    assertThat(shellCalls*.script).noneMatch { it.contains('account show --query id') }
    assertThat(withEnvVariables).contains(
      'TF_VAR_mgmt_subscription_id=',
      'TF_VAR_jenkins_AAD_objectId='
    )
  }
}
