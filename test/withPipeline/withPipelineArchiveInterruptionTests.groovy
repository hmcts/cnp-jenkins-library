package withPipeline

import groovy.mock.interceptor.StubFor
import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder

import static org.assertj.core.api.Assertions.assertThat

class withPipelineArchiveInterruptionTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withPipelineArchiveInterruptionTests() {
    super("master", jenkinsFile)

    helper.registerAllowedMethod("retry", [LinkedHashMap, Closure], { _, body -> body.call() })
    helper.registerAllowedMethod("node", [String, Closure], { _, body -> body.call() })
    helper.registerAllowedMethod("timeout", [Map, Closure], { _, body -> body.call() })
  }

  @Test
  void doesNotArchiveAnInterruptedApplicationPipeline() {
    def interruption = new FlowInterruptedException(Result.ABORTED, true)
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.setupToolVersion(1) {
      throw interruption
    }

    def caughtInterruption = null
    try {
      stubBuilder.use {
        runScript("testResources/$jenkinsFile")
      }
    } catch (FlowInterruptedException expected) {
      caughtInterruption = expected
    }

    assertThat(caughtInterruption).isSameAs(interruption)
    assertThat(binding.getVariable('currentBuild').result).isEqualTo('ABORTED')
    assertThat(helper.callStack*.methodName).doesNotContain('queueBuildArchive')
  }
}
