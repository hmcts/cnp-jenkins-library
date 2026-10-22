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
  List<Map> stashArgs = []
  List<String> unstashNames = []
  List<String> messages = []
  List<Map> findFilesArgs = []
  List<Map> workspaceFiles = []
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
    helper.registerAllowedMethod('echo', [String.class], { String message -> messages << message })
    helper.registerAllowedMethod('findFiles', [Map.class], { Map args ->
      findFilesArgs << args
      workspaceFiles
    })
    helper.registerAllowedMethod('stash', [Map.class], { Map args ->
      stashNames << args.name
      stashArgs << args
    })
    helper.registerAllowedMethod('unstash', [String.class], { String name -> unstashNames << name })
    helper.registerAllowedMethod('deleteDir', [], { -> })
    helper.registerAllowedMethod('fileExists', [String.class], { String path -> path == '.git' && gitDirectoryExists })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      shellScripts << args.script
    })
    script = loadScript('vars/withEnvironmentAgent.groovy')
  }

  @Test
  void 'terraform transfer leaves primary workspace untouched and skips stash back'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'
    binding.env.ENVIRONMENT_AGENT_STASH_INCLUDES = 'infrastructure/**,.terraform-version'
    binding.env.ENVIRONMENT_AGENT_STASH_BACK = 'false'
    binding.env.STASH_DIAGNOSTICS = 'true'
    binding.env.WORKSPACE = '/opt/jenkins/workspace/job'
    binding.env.NODE_NAME = 'primary-preview'
    List<String> deletedOnNodes = []
    List<String> directories = []
    helper.registerAllowedMethod('dir', [String.class, Closure.class], { String path, Closure body ->
      directories << path
      body()
    })
    helper.registerAllowedMethod('pwd', [], { -> '/opt/jenkins/workspace/job/infrastructure' })
    helper.registerAllowedMethod('node', [String.class, Closure.class], { String label, Closure body ->
      nodeLabels << label
      String previousNode = binding.env.NODE_NAME
      binding.env.NODE_NAME = label
      try {
        body()
      } finally {
        binding.env.NODE_NAME = previousNode
      }
    })
    helper.registerAllowedMethod('deleteDir', [], { -> deletedOnNodes << binding.env.NODE_NAME })
    def nodeInsideBody

    script.call('prod', 'civil') {
      nodeInsideBody = binding.env.NODE_NAME
    }

    assertThat(nodeInsideBody).isEqualTo('civil-prod')
    assertThat(binding.env.NODE_NAME).isEqualTo('primary-preview')
    assertThat(binding.env.BUILD_AGENT_TYPE).isEqualTo('civil')
    assertThat(deletedOnNodes).containsExactly('civil-prod', 'civil-prod')
    assertThat(directories).contains('infrastructure')
    assertThat(stashArgs).hasSize(1)
    assertThat(stashArgs[0].includes).isEqualTo('infrastructure/**,.terraform-version')
    assertThat(unstashNames).containsExactly(stashNames[0])
    assertThat(findFilesArgs).containsExactly([glob: stashArgs[0].includes, excludes: stashArgs[0].excludes])
  }

  @Test
  void 'stash diagnostics count matching files by top level folder for both transfers'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'
    binding.env.STASH_DIAGNOSTICS = 'true'
    workspaceFiles = [
      [path: 'README.md', directory: false],
      [path: 'infrastructure/main.tf', directory: false],
      [path: 'src/main/App.java', directory: false],
      [path: 'src/test/AppTest.java', directory: false],
      [path: '.yarn/cache/package.zip', directory: false],
      [path: 'src/empty', directory: true]
    ]

    script.call('prod', 'civil') {}

    assertThat(findFilesArgs).hasSize(2)
    assertThat(findFilesArgs).isEqualTo(stashArgs.collect { [glob: it.includes, excludes: it.excludes] })
    stashNames.each { name ->
      assertThat(messages).contains("Stash summary for ${name}: 5 files (recursive counts by top-level folder)".toString())
    }
    assertThat(messages).contains('  (root files): 1 files', '  .yarn/: 1 files', '  infrastructure/: 1 files', '  src/: 2 files')
  }

  @Test
  void 'stash diagnostics are disabled by default'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'

    script.call('preview', 'civil') {}

    assertThat(findFilesArgs).isEmpty()
    assertThat(stashNames).hasSize(2)
  }

  @Test
  void 'stash diagnostics report an empty stash'() {
    binding.env.STASH_DIAGNOSTICS = 'true'

    script.logStashSummary('empty', 'node_modules/**')

    assertThat(messages).containsExactly('Stash summary for empty: 0 files (recursive counts by top-level folder)')
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
  void 'matching current node label avoids reallocating node when build agent type has drifted'() {
    binding.env.BUILD_AGENT_TYPE = ''
    binding.env.NODE_LABELS = 'linux civil-preview docker'
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'

    def environmentInsideBody

    script.call('preview', 'civil') {
      environmentInsideBody = binding.env.DEPLOYMENT_ENVIRONMENT
    }

    assertThat(nodeLabels).isEmpty()
    assertThat(environmentInsideBody).isEqualTo('preview')
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
  void 'forced agent label bypasses product agent fallback'() {
    binding.env.PRODUCT_AGENT_LABEL = 'toffee-vm'

    def environmentInsideBody
    def buildAgentTypeInsideBody

    script.call('dev', 'toffee', 'ubuntu-dev') {
      environmentInsideBody = binding.env.DEPLOYMENT_ENVIRONMENT
      buildAgentTypeInsideBody = binding.env.BUILD_AGENT_TYPE
    }

    assertThat(nodeLabels).containsExactly('ubuntu-dev')
    assertThat(environmentInsideBody).isEqualTo('dev')
    assertThat(buildAgentTypeInsideBody).isEqualTo('ubuntu-dev')
  }

  @Test
  void 'forced agent label reallocates when currently on product fallback agent'() {
    binding.env.PRODUCT_AGENT_LABEL = 'toffee-stg'
    binding.env.BUILD_AGENT_TYPE = 'toffee-stg'

    def environmentInsideBody
    def buildAgentTypeInsideBody

    script.call('demo', 'toffee', 'toffee-demo') {
      environmentInsideBody = binding.env.DEPLOYMENT_ENVIRONMENT
      buildAgentTypeInsideBody = binding.env.BUILD_AGENT_TYPE
    }

    assertThat(nodeLabels).containsExactly('toffee-demo')
    assertThat(environmentInsideBody).isEqualTo('demo')
    assertThat(buildAgentTypeInsideBody).isEqualTo('toffee-demo')
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
  void 'switching environment agent preserves yarn cache but excludes install state'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'

    script.call('preview', 'civil') {
    }

    assertThat(stashArgs).hasSize(2)
    stashArgs.each { Map args ->
      assertThat(args.excludes as String).contains('.yarn_dependencies_installed')
      assertThat(args.excludes as String).doesNotContain('.yarn/cache/**')
      assertThat(args.excludes as String).contains('.yarn/install-state.gz')
      assertThat(args.excludes as String).contains('vendor/bundle/**')
      assertThat(args.excludes as String).contains('.bundle/cache/**')
    }
  }

  @Test
  void 'switching environment agent excludes gradle build output from stashes'() {
    binding.env.ENVIRONMENT_AGENT_LABEL_TEMPLATE_CIVIL = 'civil-{environment}'

    script.call('preview', 'civil') {
    }

    assertThat(stashArgs).hasSize(2)
    stashArgs.each { Map args ->
      assertThat(args.excludes as String).contains('build/**')
      assertThat(args.excludes as String).contains('**/build/**')
    }
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
