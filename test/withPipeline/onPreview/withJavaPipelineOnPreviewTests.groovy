package withPipeline.onPreview

import org.junit.Test
import withPipeline.BaseCnpPipelineTest

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.assertj.core.api.Assertions.assertThat

class withJavaPipelineOnPreviewTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"
  final static pipelineWithSecrets = "exampleJavaPipelineWithSmokeSecretControl.jenkins"

  withJavaPipelineOnPreviewTests() {
    super("PR-999", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    binding.getVariable('env').putAt('CHANGE_URL', 'http://github.com/some-repo/pr/16')
    binding.getVariable('env').putAt('CHANGE_TITLE', 'Some change')

    runScript("testResources/$jenkinsFile")

    assertThat(stageIndex('Checkout')).isNotNegative()
    assertThat(stageIndex('Build')).isGreaterThan(stageIndex('Checkout'))
    assertThat(helper.callStack.findAll { it.methodName == 'agent' }).isEmpty()
  }

  @Test
  void agentFailureIsPropagatedWithoutRetryingThePipeline() {
    int nodeCalls = 0
    helper.registerAllowedMethod('node', [String.class, Closure.class], { String label, Closure body ->
      nodeCalls++
      throw new IOException('agent disconnected')
    })
    Exception failure

    try {
      runScript("testResources/$jenkinsFile")
    } catch (Exception err) {
      failure = err
    }

    assertThat(failure).isNotNull()
    assertThat(nodeCalls).isEqualTo(1)
    assertThat(helper.callStack.findAll { it.methodName == 'agent' }).isEmpty()
  }

  @Test
  void smokeTestUsesTeamSecretsByDefault() {
    runScript("testResources/$pipelineWithSecrets")

    def secretIndexes = teamSecretIndexes()
    def smokeIndex = stageIndex('Smoke Test - AKS preview')
    def functionalIndex = stageIndex('Functional Test - preview')

    assertThat(secretIndexes).hasSize(4)
    assertThat(smokeIndex).isNotNegative()
    assertThat(functionalIndex).isNotNegative()
    assertThat(secretIndexes[1]).isLessThan(smokeIndex)
    assertThat(secretIndexes).anyMatch { it > smokeIndex && it < functionalIndex }
    assertThat(smokeIndex).isLessThan(functionalIndex)
  }

  @Test
  void smokeTestCanRunBeforeTeamSecretsAreLoaded() {
    binding.getVariable('env').putAt('DISABLE_SMOKE_TEST_SECRETS', 'true')
    runScript("testResources/$pipelineWithSecrets")

    def secretIndexes = teamSecretIndexes()
    def smokeIndex = stageIndex('Smoke Test - AKS preview')
    def functionalIndex = stageIndex('Functional Test - preview')

    assertThat(secretIndexes).hasSize(3)
    assertThat(smokeIndex).isNotNegative()
    assertThat(functionalIndex).isNotNegative()
    assertThat(smokeIndex).isLessThan(secretIndexes[1])
    assertThat(secretIndexes[1]).isLessThan(functionalIndex)
    assertThat(smokeIndex).isLessThan(functionalIndex)
  }

  private List<Integer> teamSecretIndexes() {
    (0..<helper.callStack.size()).findAll { index ->
      def call = helper.callStack[index]
      call.methodName == 'withTeamSecrets'
    }
  }

  private int stageIndex(String stageName) {
    helper.callStack.findIndexOf { call ->
      call.methodName == 'stage' && callArgsToString(call).contains(stageName)
    }
  }
}
