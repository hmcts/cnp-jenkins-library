import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class WithEnvironmentAgentTest extends BasePipelineTest {

  def script
  List<String> nodeLabels = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      BUILD_AGENT_TYPE: 'civil',
      DEPLOYMENT_ENVIRONMENT: 'aat',
      RAW_PRODUCT_NAME: 'civil',
      PRODUCT_AGENT_LABEL: 'civil'
    ])

    helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { List variables, Closure body ->
      def previousValues = [:]
      variables.each { String variable ->
        def parts = variable.split('=', 2)
        previousValues[parts[0]] = binding.env[parts[0]]
        binding.env[parts[0]] = parts.length == 2 ? parts[1] : ''
      }
      try {
        body()
      } finally {
        previousValues.each { key, value -> binding.env[key] = value }
      }
    })
    helper.registerAllowedMethod('node', [String.class, Closure.class], { String label, Closure body ->
      nodeLabels << label
      body()
    })
    script = loadScript('vars/withEnvironmentAgent.groovy')
  }

  @Test
  void 'same label environment switch updates deployment environment without reallocating node'() {
    def environmentInsideBody
    def buildAgentTypeInsideBody

    script.call('preview', 'civil') {
      environmentInsideBody = binding.env.DEPLOYMENT_ENVIRONMENT
      buildAgentTypeInsideBody = binding.env.BUILD_AGENT_TYPE
    }

    assertThat(nodeLabels).isEmpty()
    assertThat(environmentInsideBody).isEqualTo('preview')
    assertThat(buildAgentTypeInsideBody).isEqualTo('civil')
    assertThat(binding.env.DEPLOYMENT_ENVIRONMENT).isEqualTo('aat')
  }
}
