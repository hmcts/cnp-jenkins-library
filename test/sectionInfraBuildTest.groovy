import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class SectionInfraBuildTest extends BasePipelineTest {

  def script
  def callbacks = new PipelineCallbacksConfig()
  List<String> events = []
  List<Map> terraformCalls = []
  boolean terraformFails = false

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [BUSINESS_AREA_TAG: 'CFT'])
    binding.setVariable('currentBuild', [:])
    helper.registerAllowedMethod('approvedEnvironmentRepository', [String.class, MetricsPublisher.class, Closure.class], {
      String environment, MetricsPublisher publisher, Closure body -> body()
    })
    helper.registerAllowedMethod('withSubscription', [String.class, String.class, String.class, Closure.class], {
      String subscription, String product, String environment, Closure body -> body()
    })
    helper.registerAllowedMethod('timeoutWithMsg', [Map.class, Closure.class], { Map options, Closure body -> body() })
    helper.registerAllowedMethod('spinInfra', [Map.class], { Map options ->
      terraformCalls << options
      events << 'terraform'
      if (terraformFails) {
        throw new RuntimeException('terraform failed')
      }
    })
    script = loadScript('vars/sectionInfraBuild.groovy')
  }

  @Test
  void 'plan only runs invoke plan hooks instead of deployment hooks'() {
    ['prod', 'aat'].each { String environment ->
      events.clear()
      terraformCalls.clear()
      registerHooks("buildinfra:${environment}")
      registerHooks("planinfra:${environment}")

      runInfrastructure(environment, true)

      assertThat(events).containsExactly(
        "before:planinfra:${environment}".toString(),
        'terraform',
        "success:planinfra:${environment}".toString(),
        "always:planinfra:${environment}".toString()
      )
      assertThat(terraformCalls).hasSize(1)
      assertThat(terraformCalls[0].tfPlanOnly).isEqualTo(true)
    }
  }

  @Test
  void 'apply runs retain deployment hooks'() {
    registerHooks('buildinfra:prod')
    registerHooks('planinfra:prod')

    runInfrastructure('prod', false)

    assertThat(events).containsExactly('before:buildinfra:prod', 'terraform', 'success:buildinfra:prod', 'always:buildinfra:prod')
    assertThat(terraformCalls[0].tfPlanOnly).isEqualTo(false)
  }

  @Test
  void 'failed plans retain plan failure and global reporting without deployment hooks'() {
    registerHooks('buildinfra:prod')
    registerHooks('planinfra:prod')
    callbacks.bodies['onStageFailure'] = { stage -> events << "stage-failure:${stage}".toString() }
    callbacks.registerAfterAll { stage -> events << "reported:${stage}".toString() }
    terraformFails = true

    assertThatThrownBy {
      runInfrastructure('prod', true)
    }.isInstanceOf(RuntimeException).hasMessage('terraform failed')

    assertThat(events).containsExactly(
      'before:planinfra:prod', 'terraform', 'stage-failure:planinfra:prod',
      'failure:planinfra:prod', 'always:planinfra:prod', 'reported:planinfra:prod'
    )
  }

  private void registerHooks(String stage) {
    callbacks.registerBefore(stage) { events << "before:${stage}".toString() }
    callbacks.bodies["after:${stage}:success".toString()] = { events << "success:${stage}".toString() }
    callbacks.bodies["after:${stage}:failure".toString()] = { events << "failure:${stage}".toString() }
    callbacks.bodies["after:${stage}:always".toString()] = { events << "always:${stage}".toString() }
  }

  private void runInfrastructure(String environment, boolean planOnly) {
    script.call(
      environment: environment,
      subscription: environment == 'prod' ? 'prod' : 'nonprod',
      product: 'civil',
      component: 'service',
      planOnly: planOnly,
      pipelineCallbacksRunner: new PipelineCallbacksRunner(callbacks)
    )
  }
}