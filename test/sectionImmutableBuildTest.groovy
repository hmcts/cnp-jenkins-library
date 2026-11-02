import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class SectionImmutableBuildTest extends BasePipelineTest {
  def script
  def config = new AppPipelineConfig(immutableImagePromotion: true, deployableApp: false)
  def callbacks = new PipelineCallbacksConfig()
  def commands = []
  def candidates = []
  def existingFiles = ['Dockerfile', '.dockerignore'] as Set
  def digest = 'sha256:' + ('c' * 64)
  def reference = "cnpacr.azurecr.io/xui/webapp@${digest}".toString()

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [BRANCH_NAME: 'master', GIT_COMMIT: 'a' * 40, JOB_NAME: 'xui/webapp/master',
      BUILD_NUMBER: '42', LAST_COMMIT_TIMESTAMP: '20261102120000', REGISTRY_NAME: 'cnpacr',
      REGISTRY_RESOURCE_GROUP: 'acr-rg', REGISTRY_SUBSCRIPTION: 'acr-sub', GIT_URL: 'https://example.test/xui/webapp'])
    helper.registerAllowedMethod('stageWithAgent', [String.class, String.class, Closure.class],
      { String name, String product, Closure body -> body() })
    helper.registerAllowedMethod('withEnvironmentAgent', [String.class, String.class, Closure.class],
      { String environment, String product, Closure body -> body() })
    helper.registerAllowedMethod('stage', [String.class, Closure.class], { String name, Closure body -> body() })
    helper.registerAllowedMethod('onPathToLive', [Closure.class], { it() })
    helper.registerAllowedMethod('onMaster', [Closure.class], { it() })
    helper.registerAllowedMethod('onPR', [Closure.class], {})
    helper.registerAllowedMethod('when', [Boolean.class, Closure.class], { Boolean condition, Closure body -> if (condition) body() })
    helper.registerAllowedMethod('withAcrClient', [String.class, Closure.class], { String subscription, Closure body -> body() })
    helper.registerAllowedMethod('withBuildCache', [AppPipelineConfig.class, Closure.class], { AppPipelineConfig c, Closure body -> body() })
    helper.registerAllowedMethod('timeoutWithMsg', [Map.class, Closure.class], { Map options, Closure body -> body() })
    helper.registerAllowedMethod('pluginActive', [String.class, Closure.class], {})
    helper.registerAllowedMethod('parallel', [Map.class], { Map branches -> branches.findAll { it.value instanceof Closure }.each { it.value() } })
    helper.registerAllowedMethod('checkoutScm', [Map.class], {})
    helper.registerAllowedMethod('warnAboutJitpackRemoval', [Map.class], {})
    helper.registerAllowedMethod('warnAboutOldAcrReferences', [String.class], {})
    helper.registerAllowedMethod('autoDeployEnvironment', [], { null })
    helper.registerAllowedMethod('fileExists', [String.class], { existingFiles.contains(it) })
    helper.registerAllowedMethod('libraryResource', [String.class], { '' })
    helper.registerAllowedMethod('writeFile', [Map.class], {})
    helper.registerAllowedMethod('stash', [Map.class], {})
    helper.registerAllowedMethod('unstash', [String.class], {})
    helper.registerAllowedMethod('dir', [String.class, Closure.class], { String directory, Closure body -> body() })
    helper.registerAllowedMethod('pwd', [Map.class], { '/tmp/immutable-source-test' })
    helper.registerAllowedMethod('deleteDir', [], {})
    helper.registerAllowedMethod('sh', [String.class], { commands << it; '' })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      commands << args.script.toString()
      if (args.script == 'git rev-parse HEAD') return 'a' * 40
      if (args.script == 'git rev-parse HEAD^{tree}') return 'b' * 40
      if (args.script.startsWith('sha256sum ')) return ('e' * 64) + '  candidate-source.tar'
      if (args.script.contains('--query loginServer')) return 'cnpacr.azurecr.io'
      if (args.script.contains('repository show-tags')) return '["staging-aaaaaaa-20261102120000"]'
      if (args.script.contains('repository show ')) return digest
      if (args.script.startsWith('docker image inspect ')) return """{"RepoDigests":["${reference}"],"Os":"linux","Architecture":"amd64","Id":"sha256:${'d' * 64}"}"""
      return ''
    })
    helper.registerAllowedMethod('readJSON', [Map.class], { new JsonSlurper().parseText(it.text) })
    helper.registerAllowedMethod('error', [String.class], { throw new IllegalStateException(it) })
    def dsl = new AppPipelineDsl(this, callbacks, config)
    callbacks.registerAfter('dockerbuild', 'success') { candidates << dsl.immutableImageCandidate() }
    script = loadScript('vars/sectionBuildAndTest.groovy')
  }

  @Test
  void existingCommitTagCannotSkipOrReuseTheCandidateOnRerun() {
    build()
    build()
    assertThat(candidates).hasSize(2)
    assertThat(candidates[0].invocationId).isNotEqualTo(candidates[1].invocationId)
    assertThat(candidates.every { it.sourceCommit == 'a' * 40 && it.sourceTree == 'b' * 40 && it.producerBuild == '42' }).isTrue()
    assertThat(commands.count { it.contains('acr build ') }).isEqualTo(2)
    assertThat(commands.findAll { it.contains('acr build ') }.every { it.contains(' -t xui/webapp:candidate-') }).isTrue()
  }

  @Test
  void defaultConsumerStillSkipsAnExistingCommitTag() {
    config.immutableImagePromotion = false
    build()
    assertThat(candidates).isEmpty()
    assertThat(commands.any { it.contains('acr build ') }).isFalse()
    assertThat(config.imageCandidate).isEmpty()
  }

  @Test
  void unsupportedTemplateCannotProduceAnUnattestedCandidate() {
    existingFiles << 'acb.tpl.yaml'
    assertThatThrownBy { build() }.hasMessageContaining('without ACB templates')
    assertThat(candidates).isEmpty()
    assertThat(commands.any { it.contains('acr build ') }).isFalse()
  }

  @Test
  void checkoutMismatchStopsBeforeBuildingOrAssigningAnImage() {
    binding.getVariable('env').GIT_COMMIT = 'e' * 40
    assertThatThrownBy { build() }.hasMessage('Immutable image source does not match the Jenkins checkout')
    assertThat(config.imageCandidate).isEmpty()
    assertThat(commands.any { it.contains('acr build ') }).isFalse()
  }

  @Test
  void sourceArchiveSurvivesAgentTransitionAndExcludesLaterWorkspaceMutations() {
    def workspace = File.createTempDir('immutable-source-', '')
    def currentDirectory = workspace
    def stashed = [:]
    def observedSource
    def shell = { String command ->
      def process = new ProcessBuilder('sh', '-c', command).directory(currentDirectory).redirectErrorStream(true).start()
      def output = process.text
      assert process.waitFor() == 0: output
      output.trim()
    }
    try {
      new File(workspace, 'source.txt').text = 'committed source'
      shell('git init -q && git add source.txt && git -c user.name=Fixture -c user.email=fixture@example.test commit -qm source')
      binding.getVariable('env').GIT_COMMIT = shell('git rev-parse HEAD')
      helper.registerAllowedMethod('stash', [Map.class], { Map args ->
        stashed[args.name] = [name: args.includes, bytes: new File(currentDirectory, args.includes).bytes]
      })
      helper.registerAllowedMethod('unstash', [String.class], { String name ->
        new File(currentDirectory, stashed[name].name).bytes = stashed[name].bytes
      })
      helper.registerAllowedMethod('dir', [String.class, Closure.class], { String directory, Closure body ->
        def previous = currentDirectory
        try { currentDirectory = new File(directory); currentDirectory.mkdirs(); body() } finally { currentDirectory = previous }
      })
      helper.registerAllowedMethod('pwd', [Map.class], { workspace.absolutePath + '@tmp' })
      helper.registerAllowedMethod('deleteDir', [], { currentDirectory.deleteDir(); currentDirectory.mkdirs() })
      helper.registerAllowedMethod('withEnvironmentAgent', [String.class, String.class, Closure.class],
        { String environment, String product, Closure body ->
          new File(workspace, '.git').deleteDir()
          body()
        })
      callbacks.registerBefore('dockerbuild') {
        new File(workspace, 'source.txt').text = 'changed after checkout'
        new File(workspace, 'untracked.txt').text = 'not in the captured revision'
      }
      def runCommand = { String command ->
        commands << command
        if (command.contains('acr build ')) {
          observedSource = new File(currentDirectory, 'source.txt').text
          assert !new File(currentDirectory, 'untracked.txt').exists()
          assert currentDirectory != workspace
          return ''
        }
        if (command.contains('--query loginServer')) return 'cnpacr.azurecr.io'
        if (command.contains('repository show-tags')) return '[]'
        if (command.contains('repository show ')) return digest
        if (command.startsWith('docker image inspect ')) return """{"RepoDigests":["${reference}"],"Os":"linux","Architecture":"arm64","Variant":"v8","Id":"sha256:${'d' * 64}"}"""
        if (command.startsWith('docker ') || command.contains('acr login ')) return ''
        return shell(command)
      }
      helper.registerAllowedMethod('sh', [Map.class], { Map args -> runCommand(args.script.toString()) })
      helper.registerAllowedMethod('sh', [String.class], { String command -> runCommand(command) })

      build()

      assertThat(observedSource).isEqualTo('committed source')
      assertThat(candidates[0].sourceCommit).isEqualTo(binding.getVariable('env').GIT_COMMIT)
      assertThat(candidates[0].platform).isEqualTo('linux/arm64')
      assertThat(new File(workspace, 'source.txt').text).isEqualTo('changed after checkout')
    } finally {
      workspace.deleteDir()
      new File(workspace.absolutePath + '@tmp').deleteDir()
    }
  }

  private void build() {
    def builder = [setupToolVersion: {}, build: {}, test: {}, securityCheck: {}, techStackMaintenance: {}]
    script.call(pipelineCallbacksRunner: new PipelineCallbacksRunner(callbacks), appPipelineConfig: config,
      builder: builder, subscription: 'sandbox', environment: 'aat', product: 'xui', component: 'webapp')
  }
}
