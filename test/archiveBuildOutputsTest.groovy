import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.Before
import org.junit.Test

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.assertj.core.api.Assertions.assertThat

class archiveBuildOutputsTest extends BasePipelineTest {

  def script
  def archiveFailure

  @Override
  @Before
  void setUp() {
    super.setUp()
    helper.registerAllowedMethod('archiveArtifacts', [Map.class], {
      if (archiveFailure) {
        throw archiveFailure
      }
    })
    helper.registerAllowedMethod('echo', [String.class], {})
    script = loadScript('vars/archiveBuildOutputs.groovy')
  }

  @Test
  void archivesCommonBuildOutputs() {
    script.call()

    def archiveCall = helper.callStack.find { it.methodName == 'archiveArtifacts' }
    def arguments = callArgsToString(archiveCall)

    assertThat(arguments).contains('**/build/test-results/**')
    assertThat(arguments).contains('**/playwright-report/**')
    assertThat(arguments).contains('functional-output/**')
    assertThat(arguments).contains('pods-logs-*/**')
    assertThat(arguments).contains('allowEmptyArchive=true')
  }

  @Test
  void preservesAnInterruptionWhileArchivingOutputs() {
    def interruption = new FlowInterruptedException(Result.ABORTED, true)
    archiveFailure = interruption

    try {
      script.call()
    } catch (FlowInterruptedException expected) {
      assertThat(expected).isSameAs(interruption)
      return
    }

    throw new AssertionError('Expected the archive interruption to propagate')
  }
}
