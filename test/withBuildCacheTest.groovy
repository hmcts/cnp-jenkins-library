import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class withBuildCacheTest extends BasePipelineTest {

  def script
  def files = [] as Set
  def cacheCalls = []
  def shCalls = []
  def restoredFiles = [] as Set
  def events = []
  def packageJson = [:]

  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [WORKSPACE: '/workspace'])
    helper.registerAllowedMethod('fileExists', [String.class], { files.contains(it) })
    helper.registerAllowedMethod('readJSON', [Map.class], { packageJson })
    helper.registerAllowedMethod('sh', [Map.class], {
      shCalls << it
      events << 'install'
    })
    helper.registerAllowedMethod('arbitraryFileCache', [Map.class], { it })
    helper.registerAllowedMethod('cache', [Map.class, Closure.class], { args, body ->
      cacheCalls << args
      files.addAll(restoredFiles)
      events << 'restore'
      body()
    })
    script = loadScript('vars/withBuildCache.groovy')
  }

  @Test
  void 'caches the Cypress binary when Cypress is a dependency'() {
    files.addAll(['yarn.lock', 'package.json', '.nvmrc'])
    restoredFiles << 'node_modules/.bin/cypress'
    packageJson = [devDependencies: [cypress: '^15.0.0']]
    binding.setVariable('env', [WORKSPACE: '/workspace', HOME: '/home/jenkinsssh'])

    script.call([buildCache: true]) { events << 'body' }

    assertThat(events).containsExactly('restore', 'install', 'body')
    assertThat(cacheCalls[0].caches*.cacheName).containsExactly(
      'yarn-node-modules',
      'yarn-pnp',
      'cypress-binary'
    )
    assertThat(cacheCalls[0].caches[2]).containsEntry('path', '/home/jenkinsssh/.cache/Cypress')
    assertThat(cacheCalls[0].caches[2]).containsEntry('cacheValidityDecidingFile', 'yarn.lock,package.json,.yarnrc.yml')
    assertThat(shCalls).hasSize(1)
    assertThat(shCalls[0]).containsEntry('label', 'Install Cypress binary')
    assertThat(shCalls[0].script.toString()).contains(
      "export NVM_DIR='/home/jenkinsssh/.nvm'",
      '. /opt/nvm/nvm.sh',
      'nvm install',
      'node_modules/.bin/cypress install'
    )
  }

  @Test
  void 'does not add an empty Cypress cache for other yarn projects'() {
    files.addAll(['yarn.lock', 'package.json'])
    packageJson = [devDependencies: [jest: '^30.0.0']]
    boolean called = false

    script.call([buildCache: true]) { called = true }

    assertThat(called).isTrue()
    assertThat(cacheCalls[0].caches*.cacheName).containsExactly('yarn-node-modules', 'yarn-pnp')
  }

  @Test
  void 'caches yarn install output rather than committed archives'() {
    files << 'yarn.lock'
    boolean called = false

    script.call([buildCache: true]) { called = true }

    assertThat(called).isTrue()
    assertThat(cacheCalls[0].caches*.cacheName).containsExactly('yarn-node-modules', 'yarn-pnp')
    assertThat(cacheCalls[0].caches*.path).containsExactly('node_modules', '.')
    assertThat(cacheCalls[0].caches[1]).containsEntry('includes', '.pnp.cjs,.pnp.loader.mjs,.yarn/install-state.gz')
  }

  @Test
  void 'uses a workspace local Gradle cache'() {
    files << 'gradlew'
    boolean called = false

    script.call([buildCache: true]) { called = true }

    assertThat(called).isTrue()
    assertThat(binding.getVariable('env').GRADLE_USER_HOME.toString()).isEqualTo('/workspace/.gradle-user-home')
    assertThat(cacheCalls[0].caches*.path).containsExactly(
      '.gradle-user-home/caches/modules-2/files-2.1',
      '.gradle-user-home/wrapper/dists'
    )
  }

  @Test
  void 'caches yarn and Gradle dependencies together'() {
    files.addAll(['yarn.lock', 'gradlew'])
    boolean called = false

    script.call([buildCache: true]) { called = true }

    assertThat(called).isTrue()
    assertThat(cacheCalls).hasSize(1)
    assertThat(cacheCalls[0].caches*.cacheName).containsExactly(
      'yarn-node-modules',
      'yarn-pnp',
      'gradle-dependencies',
      'gradle-wrapper'
    )
  }

  @Test
  void 'does not call the plugin when caching is disabled'() {
    boolean called = false

    script.call([buildCache: false]) { called = true }

    assertThat(called).isTrue()
    assertThat(cacheCalls).isEmpty()
  }
}
