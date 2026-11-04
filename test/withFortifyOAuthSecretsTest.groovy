import com.lesfurets.jenkins.unit.BasePipelineTest
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import hudson.model.Result
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class WithFortifyOAuthSecretsTest extends BasePipelineTest {

  def script
  List<Map> boundCredentials = []
  List<String> echoCalls = []
  Exception bindFailure = null

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [:])

    helper.registerAllowedMethod('usernamePassword', [Map.class], { Map args -> args })
    helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], { List credentials, Closure body ->
      boundCredentials.addAll(credentials)
      if (bindFailure) {
        throw bindFailure
      }
      binding.env.FORTIFY_OAUTH_CLIENT_ID = 'client-id'
      body()
    })
    helper.registerAllowedMethod('echo', [String.class], { String message -> echoCalls << message })

    script = loadScript('vars/withFortifyOAuthSecrets.groovy')
  }

  @Test
  void 'binds the fortify-on-demand-oauth Jenkins credential'() {
    String clientIdInsideBody = null

    script.call { clientIdInsideBody = binding.env.FORTIFY_OAUTH_CLIENT_ID }

    assertThat(boundCredentials).hasSize(1)
    assertThat(boundCredentials[0].credentialsId).isEqualTo('fortify-on-demand-oauth')
    assertThat(clientIdInsideBody).isEqualTo('client-id')
  }

  @Test
  void 'proceeds once without OAuth credentials when the credential cannot be bound'() {
    bindFailure = new RuntimeException('Could not find credentials entry with ID fortify-on-demand-oauth')
    int bodyCalls = 0

    script.call { bodyCalls++ }

    assertThat(bodyCalls).isEqualTo(1)
    assertThat(echoCalls).hasSize(1)
    assertThat(echoCalls[0]).contains("unable to bind Jenkins credentialsId='fortify-on-demand-oauth'")
  }

  @Test
  void 'does not run the block a second time when it fails'() {
    int bodyCalls = 0

    assertThatThrownBy {
      script.call {
        bodyCalls++
        throw new RuntimeException('scan blew up')
      }
    }.hasMessageContaining('scan blew up')

    assertThat(bodyCalls).isEqualTo(1)
    assertThat(echoCalls).isEmpty()
  }

  @Test
  void 'an abort or timeout while binding is rethrown and the block does not run'() {
    bindFailure = new FlowInterruptedException(Result.ABORTED)
    int bodyCalls = 0

    assertThatThrownBy { script.call { bodyCalls++ } }.isInstanceOf(FlowInterruptedException)

    assertThat(bodyCalls).isZero()
  }
}
