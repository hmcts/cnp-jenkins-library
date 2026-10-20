package withPipeline

import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class withPipelineArchiveInterruptionTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withPipelineArchiveInterruptionTests() {
    super("master", jenkinsFile)
  }

  @Before
  void registerArchiveInterruptionSteps() {
    helper.registerAllowedMethod("node", [String, Closure], { _, body -> body.call() })
    helper.registerAllowedMethod("timeout", [Map, Closure], { _, body -> body.call() })
  }

  @Test
  void doesNotArchiveAnInterruptedApplicationPipeline() {
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

  @Test
  void queuesTheArchiveAfterASingleFailedExecution() {
    def failure = new RuntimeException('agent lost')
    def attempts = 0
    def queueCount = 0
    def failureObserved = false
    helper.registerAllowedMethod("archiveBuildOutputs", [], {})
    helper.registerAllowedMethod("build", [Map], {
      assertThat(failureObserved).isTrue()
      queueCount++
    })
    binding.getVariable('env').BUILD_URL = 'https://build.example/job/service/job/PR-1/4/'
    binding.getVariable('env').JOB_NAME = 'service/PR-1'
    binding.getVariable('env').BUILD_NUMBER = '4'

    helper.registerAllowedMethod("sh", [Map], { options ->
      if (options.script.startsWith('grep -F "JavaLanguageVersion')) {
        attempts++
        failureObserved = true
        throw failure
      }
      return options.returnStatus ? 1 : ''
    })

    try {
      runScript("testResources/$jenkinsFile")
    } catch (RuntimeException expected) {
      assertThat(expected).isSameAs(failure)
    }

    assertThat(attempts).isEqualTo(1)
    assertThat(queueCount).isEqualTo(1)
    assertThat(binding.getVariable('currentBuild').result).isEqualTo('FAILURE')
  }
}
