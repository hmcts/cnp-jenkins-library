import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner

import static org.assertj.core.api.Assertions.assertThat

class sectionDeployToAKSTest extends BasePipelineTest {
  def script
  List<String> openStages = []
  List<String> events = []
  boolean locked = false
  boolean keepHelm = false
  def failure = new RuntimeException('startup check failed')

  @Before
  void prepareDeployment() {
    super.setUp()
    binding.setVariable('env', [BRANCH_NAME: 'PR-814', CHANGE_URL: 'https://github.com/hmcts/example/pull/814',
      GIT_COMMIT: 'abcdef123', LAST_COMMIT_TIMESTAMP: '123', REGISTRY_NAME: 'registry'])
    binding.setVariable('log', [info: { message -> }])
    new GithubAPI(new Expando(env: binding.getVariable('env'), echo: { message -> })).clearLabelCache()
    helper.registerAllowedMethod('echo', [String], {})
    helper.registerAllowedMethod('sh', [Map], { options -> 'registry.azurecr.io' })
    helper.registerAllowedMethod('httpRequest', [Map], { options ->
      [status: 200, content: keepHelm ? '[{"name":"enable_keep_helm"}]' : '[]']
    })
    helper.registerAllowedMethod('withAcrClient', [Object, Closure], { subscription, body -> body() })
    helper.registerAllowedMethod('withAksClient', [Object, Object, Object, Closure], { subscription, environment, product, body -> body() })
    helper.registerAllowedMethod('lock', [String, Closure], { name, body ->
      locked = true
      try { body() } finally { locked = false }
    })
    helper.registerAllowedMethod('stageWithEnvironmentAgent', [String, String, String, Closure], { name, product, environment, body ->
      openStages << name
      try {
        body()
      } catch (Exception err) {
        events << "failed:${name}".toString()
        throw err
      } finally {
        openStages.remove(openStages.size() - 1)
      }
    })
    helper.registerAllowedMethod('withTeamSecrets', [Object, String, String, Closure], { config, environment, product, body -> body() })
    helper.registerAllowedMethod('timeoutWithMsg', [Map, Closure], { options, body -> body() })
    helper.registerAllowedMethod('onPR', [Closure], { body -> body() })
    helper.registerAllowedMethod('githubCreateDeployment', [], { -> 1 })
    helper.registerAllowedMethod('warnAboutDeprecatedChartConfig', [Map], {})
    helper.registerAllowedMethod('helmInstall', [Object, Map], { image, params -> throw failure })
    helper.registerAllowedMethod('helmUninstall', [Object, Map, Object], { image, params, callbacks ->
      assertThat(openStages).isEmpty()
      assertThat(locked).isTrue()
      events << 'uninstall'
    })
    script = loadScript('vars/sectionDeployToAKS.groovy')
  }

  @Test
  void cleanupRunsAfterTheFailedDeployStageExits() {
    runFailedDeployment()
    assertThat(events).containsExactly('failed:AKS deploy - preview', 'uninstall')
  }

  @Test
  void keepHelmLabelStillPreventsFailureCleanup() {
    keepHelm = true
    runFailedDeployment()
    assertThat(events).containsExactly('failed:AKS deploy - preview')
  }

  private void runFailedDeployment() {
    Exception caught
    try {
      script.call(appPipelineConfig: new AppPipelineConfig(),
        pipelineCallbacksRunner: new PipelineCallbacksRunner(new PipelineCallbacksConfig()),
        pipelineType: [builder: [:]], subscription: 'preview', environment: 'preview', product: 'apple', component: 'frontend')
    } catch (Exception err) {
      caught = err
    }
    assertThat(caught).isSameAs(failure)
  }
}