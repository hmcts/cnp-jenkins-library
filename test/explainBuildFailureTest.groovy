import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class explainBuildFailureTest extends BasePipelineTest {

  private Script script
  private List<Map> explainErrorCalls
  private List<String> messages

  @Before
  void setUp() {
    super.setUp()
    explainErrorCalls = []
    messages = []
    binding.setVariable('env', [:])
    helper.registerAllowedMethod('explainError', [Map.class], { Map options ->
      explainErrorCalls << options
      return 'The build failed because the tests failed.'
    })
    helper.registerAllowedMethod('echo', [String.class], { String message ->
      messages << message
    })
    script = loadScript('vars/explainBuildFailure.groovy')
  }

  @Test
  void 'explains a failed build on sandbox Jenkins'() {
    binding.env.PROD_SUBSCRIPTION_NAME = 'sandbox'

    def result = script.call()

    assertThat(result).isEqualTo('The build failed because the tests failed.')
    assertThat(explainErrorCalls).hasSize(1)
    assertThat(explainErrorCalls[0].maxLines).isEqualTo(200)
    assertThat(explainErrorCalls[0].language).isEqualTo('English')
    assertThat(explainErrorCalls[0].customContext).contains('HMCTS Jenkins pipeline')
  }

  @Test
  void 'does not invoke the plugin outside sandbox Jenkins'() {
    binding.env.PROD_SUBSCRIPTION_NAME = 'prod'

    def result = script.call()

    assertThat(result).isNull()
    assertThat(explainErrorCalls).isEmpty()
  }

  @Test
  void 'does not replace the original failure when analysis is unavailable'() {
    binding.env.PROD_SUBSCRIPTION_NAME = 'sandbox'
    helper.registerAllowedMethod('explainError', [Map.class], { Map options ->
      throw new RuntimeException('provider unavailable')
    })

    def result = script.call()

    assertThat(result).isNull()
    assertThat(messages).anyMatch { it.contains('provider unavailable') }
  }

  @Test
  void 'passes explicit options to the plugin'() {
    binding.env.PROD_SUBSCRIPTION_NAME = 'sandbox'

    script.call(maxLines: 50, language: 'Welsh', customContext: 'Focus on the test failure')

    assertThat(explainErrorCalls[0]).containsEntry('maxLines', 50)
      .containsEntry('language', 'Welsh')
      .containsEntry('customContext', 'Focus on the test failure')
  }
}
