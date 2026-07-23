import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.assertj.core.api.Assertions.assertThat

class archiveBuildOutputsTest extends BasePipelineTest {

  def script

  @Override
  @Before
  void setUp() {
    super.setUp()
    helper.registerAllowedMethod('archiveArtifacts', [Map.class], {})
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
}
