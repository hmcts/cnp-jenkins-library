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

  @Test
  void queuesTheArchiveOnlyAfterAgentRetriesAreExhausted() {
    def failure = new RuntimeException('agent lost')
    def attempts = 0
    def queueCount = 0
    def retryComplete = false
    helper.registerAllowedMethod("archiveBuildOutputs", [], {})
    helper.registerAllowedMethod("build", [Map], {
      assertThat(retryComplete).isTrue()
      queueCount++
    })
    helper.registerAllowedMethod("retry", [LinkedHashMap, Closure], { _, body ->
      try {
        attempts++
        body.call()
      } catch (RuntimeException firstFailure) {
        assertThat(queueCount).isZero()
        attempts++
        body.call()
      } finally {
        retryComplete = true
      }
    })
    binding.getVariable('env').BUILD_URL = 'https://build.example/job/service/job/PR-1/4/'
    binding.getVariable('env').JOB_NAME = 'service/PR-1'
    binding.getVariable('env').BUILD_NUMBER = '4'

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.setupToolVersion(2) {
      throw failure
    }

    try {
      stubBuilder.use {
        runScript("testResources/$jenkinsFile")
      }
    } catch (RuntimeException expected) {
      assertThat(expected).isSameAs(failure)
    }

    assertThat(attempts).isEqualTo(2)
    assertThat(queueCount).isEqualTo(1)
    assertThat(binding.getVariable('currentBuild').result).isEqualTo('FAILURE')
  }
}
