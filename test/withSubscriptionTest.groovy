import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.AgentSelector

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
      def previousValues = [:]
      variables.each { String variable ->
        def parts = variable.split('=', 2)
        previousValues[parts[0]] = binding.env[parts[0]]
        binding.env[parts[0]] = parts.length == 2 ? parts[1] : ''
      }
      try {
        body()
      } finally {
        previousValues.each { key, value -> binding.env[key] = value }
      }
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
      runEnvironmentAgent(environment, product, AgentSelector.labelForEnvironment(environment, binding.env, product), body)
    })
    helper.registerAllowedMethod('withEnvironmentAgent', [String.class, String.class, String.class, Closure.class], {
      String environment, String product, String agentLabel, Closure body ->
        runEnvironmentAgent(environment, product, agentLabel, body)
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

  private void runEnvironmentAgent(String environment, String product, String agentLabel, Closure body) {
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
      [environment: 'prod', product: 'toffee', agentLabel: 'ubuntu-prod'],
      [environment: 'ptl', product: 'toffee', agentLabel: 'ubuntu-ptl']
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
  void 'environment-like subscription uses subscription agent when environment differs'() {
    boolean bodyCalled = false
    binding.env.PRODUCT_AGENT_LABEL = 'toffee-vm'

    script.call('dev', 'toffee', 'prod') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(subscriptionLoginCalls).containsExactly([
      subscription: 'dev',
      buildAgentType: 'ubuntu-dev',
      deploymentEnvironment: 'dev'
    ])
    assertThat(environmentAgentCalls).containsExactly(
      [environment: 'dev', product: 'toffee', agentLabel: 'ubuntu-dev'],
      [environment: 'ptl', product: 'toffee', agentLabel: 'ubuntu-ptl']
    )
    assertThat(shellCalls*.script).anyMatch {
      it.contains('AZURE_CONFIG_DIR=/opt/jenkins/.azure-dev') &&
        it.contains('storage account keys list') &&
        it.contains('--resource-group mgmt-state-store-dev')
    }
    assertThat(withEnvVariables).doesNotContain('PRODUCT_AGENT_LABEL=')
  }

  @Test
  void 'product-only flow uses current deployment environment for env-like subscription routing'() {
    boolean bodyCalled = false
    binding.env.DEPLOYMENT_ENVIRONMENT = 'stg'
    binding.env.PRODUCT_AGENT_LABEL = 'toffee-vm'

    script.call('dev', 'toffee') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(subscriptionLoginCalls).containsExactly([
      subscription: 'dev',
      buildAgentType: 'ubuntu-dev',
      deploymentEnvironment: 'dev'
    ])
    assertThat(environmentAgentCalls).containsExactly(
      [environment: 'dev', product: 'toffee', agentLabel: 'ubuntu-dev'],
      [environment: 'ptl', product: 'toffee', agentLabel: 'ubuntu-ptl']
    )
  }

  @Test
  void 'CFT-style subscription falls back to environment agent routing'() {
    boolean bodyCalled = false

    script.call('nonprod', 'civil', 'aat') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(subscriptionLoginCalls).containsExactly([
      subscription: 'nonprod',
      buildAgentType: 'ubuntu-aat',
      deploymentEnvironment: 'aat'
    ])
    assertThat(environmentAgentCalls).containsExactly(
      [environment: 'aat', product: 'civil', agentLabel: 'ubuntu-aat'],
      [environment: 'ptl', product: 'civil', agentLabel: 'ubuntu-ptl']
    )
    assertThat(withEnvVariables).doesNotContain('PRODUCT_AGENT_LABEL=')
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
    assertThat(environmentAgentCalls).containsExactly([environment: 'sbox', product: 'plum', agentLabel: 'ubuntu-sbox'])
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
