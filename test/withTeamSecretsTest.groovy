import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class WithTeamSecretsTest extends BasePipelineTest {

  def script
  List<Map> keyVaultCalls = []
  List<Map> shellCalls = []
  List<String> echoCalls = []
  List<List<String>> withEnvCalls = []
  boolean failAzLogin = false
  Set<String> forbiddenAzureConfigNames = [] as Set

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      AZURE_CLIENT_ID: 'ptl-client-id',
      AZURE_CLIENT_SECRET: 'ptl-client-secret'
    ])

    helper.registerAllowedMethod('withAzureKeyvault', [LinkedHashMap, Closure.class], { Map args, Closure body ->
      keyVaultCalls << args
      body()
    })
    helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { List<String> variables, Closure body ->
      withEnvCalls << variables
      Map previousValues = [:]
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
    helper.registerAllowedMethod('echo', [String.class], { String message ->
      echoCalls << message
    })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellCalls << args
      if (failAzLogin && args.script.contains('az login --identity')) {
        throw new RuntimeException('az login failed')
      }
      if (args.script.contains('keyvault secret show')) {
        if (forbiddenAzureConfigNames.any { String azureConfigName -> args.script.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-${azureConfigName}'") }) {
          return forbiddenSentinelFrom(args.script)
        }
        if (args.script.contains("--name 'second-secret'")) {
          return 'second-secret-value'
        }
        return 'case-document-secret'
      }
      return ''
    })

    script = loadScript('vars/withTeamSecrets.groovy')
  }

  @Test
  void 'legacy VM agent secret loading keeps explicit Azure app credentials'() {
    boolean bodyCalled = false

    script.call(config(), 'aat') {
      bodyCalled = true
    }

    assertThat(bodyCalled).isTrue()
    assertThat(keyVaultCalls).hasSize(1)
    assertThat(keyVaultCalls[0].keyVaultURLOverride).isEqualTo('https://civil-aat.vault.azure.net/')
    assertThat(keyVaultCalls[0].applicationIDOverride).isEqualTo('ptl-client-id')
    assertThat(keyVaultCalls[0].applicationSecretOverride).isEqualTo('ptl-client-secret')
  }

  @Test
  void 'environment agent secret loading uses agent managed identity'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    boolean bodyCalled = false
    String secretValueInsideBody

    script.call(config(), 'preview', 'civil') {
      bodyCalled = true
      secretValueInsideBody = binding.env.CASE_DOCUMENT_AM_API_S2S_SECRET
    }

    assertThat(bodyCalled).isTrue()
    assertThat(secretValueInsideBody).isEqualTo('case-document-secret')
    assertThat(keyVaultCalls).isEmpty()
    assertThat(shellCalls*.script).anyMatch { it.contains("set +x") && it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-preview' az login --identity >/dev/null") }
    assertThat(shellCalls*.script).anyMatch { it.contains("az keyvault secret show --vault-name 'civil-aat' --name 'case-document-am-api-s2s-secret'") }
    assertThat(withEnvCalls.toString()).contains('CASE_DOCUMENT_AM_API_S2S_SECRET=case-document-secret')
  }

  @Test
  void 'environment agent secret loading iterates across multiple key vaults'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    boolean bodyCalled = false

    script.call(multiVaultConfig(), 'preview', 'civil') {
      bodyCalled = true
      assertThat(binding.env.CASE_DOCUMENT_AM_API_S2S_SECRET).isEqualTo('case-document-secret')
      assertThat(binding.env.SECOND_SECRET).isEqualTo('second-secret-value')
    }

    assertThat(bodyCalled).isTrue()
    assertThat(keyVaultCalls).isEmpty()
    assertThat(shellCalls*.script.findAll { it.contains('az login --identity') }).hasSize(2)
    assertThat(shellCalls*.script).anyMatch { it.contains("az keyvault secret show --vault-name 'civil-aat' --name 'case-document-am-api-s2s-secret'") }
    assertThat(shellCalls*.script).anyMatch { it.contains("az keyvault secret show --vault-name 'civil-shared-aat' --name 'second-secret'") }
  }

  @Test
  void 'preview environment agent falls back to aat managed identity when aat vault read is forbidden'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    forbiddenAzureConfigNames = ['preview'] as Set
    boolean bodyCalled = false

    script.call(config(), 'preview', 'civil') {
      bodyCalled = true
      assertThat(binding.env.CASE_DOCUMENT_AM_API_S2S_SECRET).isEqualTo('case-document-secret')
    }

    assertThat(bodyCalled).isTrue()
    assertThat(keyVaultCalls).isEmpty()
    assertThat(shellCalls*.script).anyMatch { it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-preview' az login --identity >/dev/null") }
    assertThat(shellCalls*.script).anyMatch { it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-aat' az login --identity >/dev/null") }
    assertThat(shellCalls*.script).anyMatch {
      it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-preview'") &&
        it.contains("az keyvault secret show --vault-name 'civil-aat' --name 'case-document-am-api-s2s-secret'")
    }
    assertThat(shellCalls*.script).anyMatch {
      it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-aat'") &&
        it.contains("az keyvault secret show --vault-name 'civil-aat' --name 'case-document-am-api-s2s-secret'")
    }
    assertThat(echoCalls).anyMatch { it.contains('retrying with AAT Jenkins MI') }
  }

  @Test
  void 'preview environment agent does not fall back for non aat vaults'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    forbiddenAzureConfigNames = ['preview'] as Set

    assertThatThrownBy {
      script.call(nonAatPreviewConfig(), 'preview', 'civil') {}
    }.isInstanceOf(RuntimeException)
      .hasMessageContaining('returned Forbidden and no retry applies')

    assertThat(shellCalls*.script).noneMatch { it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-aat' az login --identity") }
  }

  @Test
  void 'non preview environment agent does not fall back for aat vaults'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-aat'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'aat'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    forbiddenAzureConfigNames = ['aat'] as Set

    assertThatThrownBy {
      script.call(config(), 'aat', 'civil') {}
    }.isInstanceOf(RuntimeException)
      .hasMessageContaining('returned Forbidden and no retry applies')

    assertThat(echoCalls).isEmpty()
  }

  @Test
  void 'preview environment agent reports when aat fallback is also denied'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    forbiddenAzureConfigNames = ['preview', 'aat'] as Set

    assertThatThrownBy {
      script.call(config(), 'preview', 'civil') {}
    }.isInstanceOf(RuntimeException)
      .hasMessageContaining('AAT Jenkins MI retry was also denied')
      .hasMessageContaining('Check Key Vault access policies')
  }

  @Test
  void 'preview environment agent falls back once for multiple secrets in one aat vault'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    forbiddenAzureConfigNames = ['preview'] as Set
    boolean bodyCalled = false

    script.call(multiSecretSingleVaultConfig(), 'preview', 'civil') {
      bodyCalled = true
      assertThat(binding.env.CASE_DOCUMENT_AM_API_S2S_SECRET).isEqualTo('case-document-secret')
      assertThat(binding.env.SECOND_SECRET).isEqualTo('second-secret-value')
    }

    assertThat(bodyCalled).isTrue()
    assertThat(shellCalls*.script.findAll { it.contains("AZURE_CONFIG_DIR='/opt/jenkins/.azure-aat' az login --identity") }).hasSize(1)
    assertThat(echoCalls.findAll { it.contains('retrying with AAT Jenkins MI') }).hasSize(1)
    assertThat(withEnvCalls.toString()).contains('CASE_DOCUMENT_AM_API_S2S_SECRET=case-document-secret')
    assertThat(withEnvCalls.toString()).contains('SECOND_SECRET=second-secret-value')
  }

  @Test
  void 'environment agent secret loading propagates az login failures'() {
    binding.env.BUILD_AGENT_TYPE = 'civil-preview'
    binding.env.DEPLOYMENT_ENVIRONMENT = 'preview'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-${environment}'
    failAzLogin = true

    assertThatThrownBy {
      script.call(config(), 'preview', 'civil') {}
    }.isInstanceOf(RuntimeException)
      .hasMessageContaining('az login failed')
  }

  private Map config() {
    [
      vaultSecrets: [
        'civil-${env}': [
          [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'case-document-am-api-s2s-secret', envVariable: 'CASE_DOCUMENT_AM_API_S2S_SECRET']
        ]
      ],
      vaultEnvironmentOverrides: ['preview': 'aat', 'dev': 'stg']
    ]
  }

  private Map nonAatPreviewConfig() {
    [
      vaultSecrets: [
        'civil-${env}': [
          [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'case-document-am-api-s2s-secret', envVariable: 'CASE_DOCUMENT_AM_API_S2S_SECRET']
        ]
      ],
      vaultEnvironmentOverrides: ['preview': 'stg']
    ]
  }

  private Map multiVaultConfig() {
    [
      vaultSecrets: [
        'civil-${env}': [
          [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'case-document-am-api-s2s-secret', envVariable: 'CASE_DOCUMENT_AM_API_S2S_SECRET']
        ],
        'civil-shared-${env}': [
          [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'second-secret', envVariable: 'SECOND_SECRET']
        ]
      ],
      vaultEnvironmentOverrides: ['preview': 'aat', 'dev': 'stg']
    ]
  }

  private Map multiSecretSingleVaultConfig() {
    [
      vaultSecrets: [
        'civil-${env}': [
          [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'case-document-am-api-s2s-secret', envVariable: 'CASE_DOCUMENT_AM_API_S2S_SECRET'],
          [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'second-secret', envVariable: 'SECOND_SECRET']
        ]
      ],
      vaultEnvironmentOverrides: ['preview': 'aat', 'dev': 'stg']
    ]
  }

  private String forbiddenSentinelFrom(String script) {
    def matcher = script =~ /echo '(__HMCTS_KEYVAULT_FORBIDDEN_[^']+)'/
    assertThat(matcher.find()).isTrue()
    return matcher.group(1)
  }
}
