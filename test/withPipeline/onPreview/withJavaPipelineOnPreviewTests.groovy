package withPipeline.onPreview

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
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

    def stubBuilder = javaBuilderStub()

    binding.getVariable('env').putAt('CHANGE_URL', 'http://github.com/some-repo/pr/16')
    binding.getVariable('env').putAt('CHANGE_TITLE', 'Some change')

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }

  @Test
  void smokeTestUsesTeamSecretsByDefault() {
    enablePipelineExecution()

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
    enablePipelineExecution()

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

  private StubFor javaBuilderStub() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      techStackMaintenance(0) {}
      sonarScan(0) {}
      smokeTest(0) {}
      e2eTest(0) {}
      functionalTest(0) {}
    }
    stubBuilder
  }

  private void enablePipelineExecution() {
    helper.registerAllowedMethod('retry', [LinkedHashMap.class, Closure.class], { Map ignored, Closure body ->
      body.call()
    })
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
