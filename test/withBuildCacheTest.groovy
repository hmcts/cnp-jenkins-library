import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class withBuildCacheTest extends BasePipelineTest {

  def script
  def files = [] as Set
  def cacheCalls = []

  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [WORKSPACE: '/workspace'])
    helper.registerAllowedMethod('fileExists', [String.class], { files.contains(it) })
    helper.registerAllowedMethod('arbitraryFileCache', [Map.class], { it })
    helper.registerAllowedMethod('cache', [Map.class, Closure.class], { args, body ->
      cacheCalls << args
      body()
    })
    script = loadScript('vars/withBuildCache.groovy')
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
  void 'does not call the plugin when caching is disabled'() {
    boolean called = false

    script.call([buildCache: false]) { called = true }

    assertThat(called).isTrue()
    assertThat(cacheCalls).isEmpty()
  }
}
