package withNightlyPipeline

import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.Before
import org.junit.Test
import withPipeline.BaseCnpPipelineTest

import static org.assertj.core.api.Assertions.assertThat

class withNightlyPipelineArchiveInterruptionTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaNightlyPipeline.jenkins"

  withNightlyPipelineArchiveInterruptionTests() {
    super("master", jenkinsFile)
  }

  @Before
  void registerArchiveInterruptionSteps() {
    helper.registerAllowedMethod("node", [String, Closure], { _, body -> body.call() })
    helper.registerAllowedMethod("timeout", [Map, Closure], { _, body -> body.call() })
  }

  @Test
  void doesNotArchiveAnInterruptedNightlyPipeline() {
    def interruption = new FlowInterruptedException(Result.ABORTED, true)
    helper.registerAllowedMethod("sh", [Map], { options ->
      if (options.script.startsWith('grep -F "JavaLanguageVersion')) {
        throw interruption
      }
      return options.returnStatus ? 1 : ''
    })

    def caughtInterruption = null
    try {
      runScript("testResources/$jenkinsFile")
    } catch (FlowInterruptedException expected) {
      caughtInterruption = expected
    }

    assertThat(caughtInterruption).isSameAs(interruption)
    assertThat(binding.getVariable('currentBuild').result).isEqualTo('ABORTED')
    assertThat(helper.callStack*.methodName).doesNotContain('queueBuildArchive')
  }
}
