import com.lesfurets.jenkins.unit.BasePipelineTest
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import hudson.model.Result
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class WithFortifySecretsTest extends BasePipelineTest {

  // Message shapes produced by azure-keyvault-plugin
  static final String NOT_FOUND = 'Failed to retrieve secret fortify-on-demand-username from vault https://amp-marketplace-aat.vault.azure.net, ' +
    'error message: Status code 404, "{"error":{"code":"SecretNotFound","message":"A secret with (name/id) fortify-on-demand-username was not found in this key vault."}}"'
  static final String FORBIDDEN = 'Failed to retrieve secret fortify-on-demand-username from vault https://amp-marketplace-aat.vault.azure.net, ' +
    'error message: Status code 403, "{"error":{"code":"Forbidden","message":"The user, group or application does not have secrets get permission on key vault."}}"'

  def script
  List<Map> keyVaultCalls = []
  List<String> echoCalls = []
  Exception keyVaultFailure = null

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [:])

    helper.registerAllowedMethod('withAzureKeyvault', [LinkedHashMap, Closure.class], { Map args, Closure body ->
      keyVaultCalls << args
      if (keyVaultFailure) {
        throw keyVaultFailure
      }
      args.azureKeyVaultSecrets.each { Map secret -> binding.env[secret.envVariable] = "${secret.name}-value" }
      body()
    })
    helper.registerAllowedMethod('echo', [String.class], { String message -> echoCalls << message })

    script = loadScript('vars/withFortifySecrets.groovy')
  }

  @Test
  void 'loads scan credentials from the named vault'() {
    String userInsideBody = null

    script.call('amp-marketplace-aat') { userInsideBody = binding.env.FORTIFY_USER_NAME }

    assertThat(keyVaultCalls).hasSize(1)
    assertThat(keyVaultCalls[0].keyVaultURLOverride).isEqualTo('https://amp-marketplace-aat.vault.azure.net')
    assertThat(userInsideBody).isEqualTo('fortify-on-demand-username-value')
    assertThat(echoCalls).isEmpty()
  }

  @Test
  void 'runs the block exactly once without scan credentials when the vault does not hold them'() {
    keyVaultFailure = new RuntimeException(NOT_FOUND)
    int bodyCalls = 0
    String userInsideBody = 'unset'

    script.call('amp-marketplace-aat') {
      bodyCalls++
      userInsideBody = binding.env.FORTIFY_USER_NAME
    }

    assertThat(bodyCalls).isEqualTo(1)
    assertThat(userInsideBody).isNull()
    assertThat(echoCalls).hasSize(1)
    assertThat(echoCalls[0]).startsWith('WARNING: Fortify: fortify-on-demand-username/password not found in Key Vault amp-marketplace-aat')
    assertThat(echoCalls[0]).contains('repo-level fortifyScan task will fail to authenticate')
  }

  @Test
  void 'recognises a missing secret by the Azure SDK ResourceNotFoundException cause'() {
    keyVaultFailure = new RuntimeException('Failed to retrieve secret', new ResourceNotFoundException('opaque'))
    int bodyCalls = 0

    script.call('amp-marketplace-aat') { bodyCalls++ }

    assertThat(bodyCalls).isEqualTo(1)
  }

  @Test
  void 'still fails when the vault cannot be read for any other reason'() {
    keyVaultFailure = new RuntimeException(FORBIDDEN)
    int bodyCalls = 0

    assertThatThrownBy { script.call('amp-marketplace-aat') { bodyCalls++ } }.hasMessageContaining('Status code 403')

    assertThat(bodyCalls).isZero()
    assertThat(echoCalls).isEmpty()
  }

  @Test
  void 'an abort or timeout while loading secrets is rethrown and the block does not run'() {
    keyVaultFailure = new FlowInterruptedException(Result.ABORTED)
    int bodyCalls = 0

    assertThatThrownBy { script.call('amp-marketplace-aat') { bodyCalls++ } }.isInstanceOf(FlowInterruptedException)

    assertThat(bodyCalls).isZero()
    assertThat(echoCalls).isEmpty()
  }

  @Test
  void 'does not swallow or retry failures raised inside the block'() {
    int bodyCalls = 0

    assertThatThrownBy {
      script.call('amp-marketplace-aat') {
        bodyCalls++
        throw new RuntimeException('scan blew up')
      }
    }.hasMessageContaining('scan blew up')

    assertThat(bodyCalls).isEqualTo(1)
    assertThat(echoCalls).isEmpty()
  }

  @Test
  void 'does not retry a block that fails in the missing-secrets fallback'() {
    keyVaultFailure = new RuntimeException(NOT_FOUND)
    int bodyCalls = 0

    assertThatThrownBy {
      script.call('amp-marketplace-aat') {
        bodyCalls++
        throw new RuntimeException('scan blew up')
      }
    }.hasMessageContaining('scan blew up')

    assertThat(bodyCalls).isEqualTo(1)
  }

  @Test
  void 'skips the vault when no vault name is given'() {
    int bodyCalls = 0

    script.call('') { bodyCalls++ }

    assertThat(bodyCalls).isEqualTo(1)
    assertThat(keyVaultCalls).isEmpty()
    assertThat(echoCalls).containsExactly('Fortify: missing fortifyVaultName; proceeding without Fortify scan credentials')
  }

  // Stand-in for com.azure.core.exception.ResourceNotFoundException (matched by simple name)
  static class ResourceNotFoundException extends RuntimeException {
    ResourceNotFoundException(String message) { super(message) }
  }
}
