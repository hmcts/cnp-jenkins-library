import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.contino.AppPipelineConfig

import static org.assertj.core.api.Assertions.assertThat

class withPipelineAgentDisconnectTest extends BasePipelineTest {

  def script
  List<Map> uninstalled = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [:])
    binding.setVariable('helmUninstall', [afterAgentDisconnect: { image, release -> uninstalled << release }])
    helper.registerAllowedMethod('echo', [String.class], {})
    helper.registerAllowedMethod('sleep', [Map.class], {})
    helper.registerAllowedMethod('node', [String.class, Closure.class], { String label, Closure body -> body.call() })
    script = loadScript('vars/withPipeline.groovy')
  }

  @Test
  void waitsForTheConfiguredPeriodWhenTheAgentDoesNotReconnect() {
    script.handleAgentDisconnect('agent-1', 2, [isAgentOnline: { String name -> false }], [], 'ubuntu')

    assertThat(helper.callStack.findAll { it.methodName == 'sleep' }).hasSize(8)
    assertThat(helper.callStack.findAll { it.methodName == 'node' }).isEmpty()
    assertThat(helper.callStack.find { it.methodName == 'stage' }.args[0].toString())
      .isEqualTo('Waiting 2 minutes for agent to reconnect')
  }

  @Test
  void stopsWaitingWhenTheAgentReconnects() {
    script.handleAgentDisconnect('agent-1', 2, [isAgentOnline: { String name -> true }], [], 'ubuntu')

    assertThat(helper.callStack.findAll { it.methodName == 'sleep' }).hasSize(1)
  }

  @Test
  void uninstallsPendingHelmReleasesOnAFreshAgentAfterWaiting() {
    def release = [dockerImage: 'image', environment: 'preview']
    def pending = [release]

    script.handleAgentDisconnect('agent-1', 1, [isAgentOnline: { String name -> false }], pending, 'ubuntu')

    assertThat(uninstalled).containsExactly(release)
    assertThat(pending).isEmpty()
    def lastSleep = helper.callStack.findLastIndexOf { it.methodName == 'sleep' }
    def node = helper.callStack.findIndexOf { it.methodName == 'node' }
    assertThat(node).isGreaterThan(lastSleep)
  }

  @Test
  void waitPeriodDefaultsToTwoMinutesAndCanBeOverridden() {
    def config = new AppPipelineConfig()
    assertThat(script.agentDisconnectWaitMinutes(config)).isEqualTo(2)

    config.agentDisconnectWaitMinutes = 5
    assertThat(script.agentDisconnectWaitMinutes(config)).isEqualTo(5)

    binding.getVariable('env').AGENT_DISCONNECT_WAIT_MINUTES = '0'
    assertThat(script.agentDisconnectWaitMinutes(config)).isEqualTo(0)

    binding.getVariable('env').AGENT_DISCONNECT_WAIT_MINUTES = 'invalid'
    assertThat(script.agentDisconnectWaitMinutes(config)).isEqualTo(5)
  }
}
