import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class WithGradleAgentRetryTest extends BasePipelineTest {

  def script
  List<String> events = []
  List<String> nodeSelectors = []
  List<Map> retryOptions = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('scm', 'scm-source')
    binding.setVariable('env', [
      BUILD_AGENT_TYPE: 'ubuntu-sbox',
      DEPLOYMENT_ENVIRONMENT: 'sbox',
      PRODUCT_AGENT_LABEL: 'ubuntu-sbox'
    ])

    helper.registerAllowedMethod('retry', [Map.class, Closure.class], { Map options, Closure body ->
      retryOptions << options
      try {
        body()
      } catch (Exception ignored) {
        body()
      }
    })
    helper.registerAllowedMethod('agent', [], { -> 'agent-condition' })
    helper.registerAllowedMethod('node', [String.class, Closure.class], { String selector, Closure body ->
      nodeSelectors << selector
      body()
    })
    helper.registerAllowedMethod('deleteDir', [], { -> events << 'deleteDir' })
    helper.registerAllowedMethod('checkout', [Object.class], { Object source -> events << "checkout:${source}".toString() })

    script = loadScript('vars/withGradleAgentRetry.groovy')
  }

  @Test
  void 'successful first attempt uses the existing agent and workspace'() {
    script.call('toffee', 'sbox') {
      events << 'body'
    }

    assertThat(nodeSelectors).isEmpty()
    assertThat(events).containsExactly('body')
  }

  @Test
  void 'agent retry allocates and prepares a workspace only after failure'() {
    int bodyAttempts = 0

    script.call('toffee', 'sbox') {
      bodyAttempts++
      events << "body:${bodyAttempts}".toString()
      if (bodyAttempts == 1) {
        throw new IOException('simulated agent interruption')
      }
    }

    assertThat(retryOptions).containsExactly([
      conditions: ['agent-condition'],
      count: 2
    ])
    assertThat(nodeSelectors).containsExactly('ubuntu-sbox && !nightly')
    assertThat(events).containsExactly(
      'body:1',
      'deleteDir', 'checkout:scm-source', 'body:2'
    )
  }
}