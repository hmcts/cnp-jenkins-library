import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class WithEnvironmentAgentTest extends BasePipelineTest {

  def script
  List<String> nodeLabels = []
  List<String> shellScripts = []
  List<String> stashNames = []
  List<String> unstashNames = []
  boolean gitDirectoryExists = false

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
    helper.registerAllowedMethod('pwd', [], { -> '/opt/jenkins/workspace/job' })
    helper.registerAllowedMethod('stash', [Map.class], { Map args -> stashNames << args.name })
    helper.registerAllowedMethod('unstash', [String.class], { String name -> unstashNames << name })
    helper.registerAllowedMethod('deleteDir', [], { -> })
    helper.registerAllowedMethod('fileExists', [String.class], { String path -> path == '.git' && gitDirectoryExists })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellScripts << args.script
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

  @Test
  void 'switching environment agent restores minimal git metadata after unstash'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'
    binding.env.ORIGINAL_REMOTE_URL = 'https://github.com/HMCTS/civil-service.git'

    def environmentInsideBody
    def buildAgentTypeInsideBody

    script.call('preview', 'civil') {
      environmentInsideBody = binding.env.DEPLOYMENT_ENVIRONMENT
      buildAgentTypeInsideBody = binding.env.BUILD_AGENT_TYPE
    }

    assertThat(nodeLabels).containsExactly('civil-preview')
    assertThat(environmentInsideBody).isEqualTo('preview')
    assertThat(buildAgentTypeInsideBody).isEqualTo('civil-preview')
    assertThat(shellScripts.join('\n')).contains('git init -q')
    assertThat(shellScripts.join('\n')).contains("git remote add origin 'https://github.com/HMCTS/civil-service.git'")
  }

  @Test
  void 'switching environment agent propagates generated files back to original workspace'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'
    binding.env.ORIGINAL_REMOTE_URL = 'https://github.com/HMCTS/civil-service.git'

    script.call('preview', 'civil') {
    }

    assertThat(stashNames).hasSize(2)
    assertThat(stashNames[1]).isEqualTo("${stashNames[0]}-updated")
    assertThat(unstashNames).containsExactly(stashNames[0], stashNames[1])
  }

  @Test
  void 'failing environment agent body skips workspace propagation back'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'
    binding.env.ORIGINAL_REMOTE_URL = 'https://github.com/HMCTS/civil-service.git'

    assertThatThrownBy {
      script.call('preview', 'civil') {
        throw new RuntimeException('boom')
      }
    }.isInstanceOf(RuntimeException)
      .hasMessage('boom')

    assertThat(stashNames).hasSize(1)
    assertThat(unstashNames).containsExactly(stashNames[0])
  }

  @Test
  void 'switching environment agent does not restore git metadata when git directory exists'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'
    binding.env.ORIGINAL_REMOTE_URL = 'https://github.com/HMCTS/civil-service.git'
    gitDirectoryExists = true

    script.call('preview', 'civil') {
    }

    assertThat(nodeLabels).containsExactly('civil-preview')
    assertThat(shellScripts).isEmpty()
  }

  @Test
  void 'switching environment agent does not restore git metadata without remote url'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'

    script.call('preview', 'civil') {
    }

    assertThat(nodeLabels).containsExactly('civil-preview')
    assertThat(shellScripts).isEmpty()
  }
}
