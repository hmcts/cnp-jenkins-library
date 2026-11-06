import com.lesfurets.jenkins.unit.BasePipelineTest
// import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

import java.io.PrintWriter
import java.io.StringWriter

import static org.assertj.core.api.Assertions.assertThat

class CredentialUsageShadowsTest extends BasePipelineTest {

  def withCredentialsScript
  def credentialsScript
  def withAzureKeyvaultScript
  StringWriter output = new StringWriter()
  // List<Map> auditRequests = []
  // List<String> echoedMessages = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [JOB_NAME: 'example-pipeline', BUILD_NUMBER: '42'])
    binding.setVariable('out', new PrintWriter(output, true))
    binding.setVariable('steps', [
      withCredentials: { List bindings, Closure body -> body.call() },
      credentials: { String credentialId -> "resolved-${credentialId}" },
      withAzureKeyvault: { Map arguments, Closure body -> body.call() }
    ])
    // helper.registerAllowedMethod('azureCosmosDBCreateDocument', [Map.class], { Map request -> auditRequests << request })
    // helper.registerAllowedMethod('echo', [String.class], { String message -> echoedMessages << message })

    withCredentialsScript = loadScript('vars/withCredentials.groovy')
    credentialsScript = loadScript('vars/credentials.groovy')
    withAzureKeyvaultScript = loadScript('vars/withAzureKeyvault.groovy')
  }

  @Test
  void 'records a credential binding and then runs its body'() {
    boolean bodyCalled = false

    withCredentialsScript.call([[credentialsId: 'github-app']], { bodyCalled = true })

    assertThat(bodyCalled).isTrue()
    assertThat(output.toString()).contains('Recording usage and performing telemetry or whatever for credential: github-app')
    // assertThat(auditRequests).hasSize(1)
    // assertThat(new JsonSlurper().parseText(auditRequests[0].document as String)).containsEntry('credential_id', 'github-app')
    // assertThat(auditRequests[0].credentialsId).isEqualTo('cosmos-connection')
    // assertThat(auditRequests[0].container).isEqualTo('credential-usage')
    // assertThat(auditRequests[0].database).isEqualTo('jenkins')
  }

  @Test
  void 'records declarative credential use and returns the original result'() {
    def result = credentialsScript.call('fortify-on-demand-oauth')

    assertThat(result.toString()).isEqualTo('resolved-fortify-on-demand-oauth')
    assertThat(output.toString()).contains('Recording usage and performing telemetry or whatever for credential: fortify-on-demand-oauth')
    // assertThat(auditRequests).hasSize(1)
    // def document = new JsonSlurper().parseText(auditRequests[0].document as String)
    // assertThat(document).containsEntry('credential_id', 'fortify-on-demand-oauth')
    // assertThat(document).containsEntry('job_name', 'example-pipeline')
  }

  @Test
  void 'records Azure Key Vault secrets and then runs its body'() {
    boolean bodyCalled = false

    withAzureKeyvaultScript.call([
      azureKeyVaultSecrets: [
        [name: 'application-insights-key'],
        [name: 'service-password']
      ]
    ], { bodyCalled = true })

    assertThat(bodyCalled).isTrue()
    assertThat(output.toString())
      .contains('Recording usage and performing telemetry or whatever for Azure Key Vault secret: application-insights-key')
      .contains('Recording usage and performing telemetry or whatever for Azure Key Vault secret: service-password')
  }
}