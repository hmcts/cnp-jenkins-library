import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class SectionImmutablePromotionTest extends BasePipelineTest {
  def script
  def config = new AppPipelineConfig(immutableImagePromotion: true)
  def callbacks = new PipelineCallbacksConfig()
  def commands = []
  def digest = 'sha256:' + ('c' * 64)
  def reference = "cnpacr.azurecr.io/xui/webapp@${digest}".toString()
  def dsl

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [BRANCH_NAME: 'master', GIT_COMMIT: 'a' * 40, JOB_NAME: 'xui/webapp/master',
      BUILD_NUMBER: '42', LAST_COMMIT_TIMESTAMP: '20261102120000', REGISTRY_NAME: 'cnpacr',
      REGISTRY_RESOURCE_GROUP: 'acr-rg', REGISTRY_SUBSCRIPTION: 'acr-sub'])
    config.imageCandidate = [sourceCommit: 'a' * 40, sourceTree: 'b' * 40, producerJob: 'xui/webapp/master',
      producerBuild: '42', registry: 'cnpacr.azurecr.io', repository: 'xui/webapp', digest: digest, imageReference: reference]
    dsl = new AppPipelineDsl(this, callbacks, config)
    helper.registerAllowedMethod('fileExists', [String.class], { it == 'Dockerfile' })
    helper.registerAllowedMethod('stageWithEnvironmentAgent', [String.class, String.class, String.class, Closure.class],
      { String name, String product, String environment, Closure body -> body() })
    helper.registerAllowedMethod('withAcrClient', [String.class, Closure.class], { String subscription, Closure body -> body() })
    helper.registerAllowedMethod('sh', [Map.class], { Map args ->
      commands << args.script.toString()
      return args.script.contains('--query loginServer') ? 'cnpacr.azurecr.io' : ''
    })
    helper.registerAllowedMethod('error', [String.class], { throw new IllegalStateException(it) })
    script = loadScript('vars/sectionPromoteBuildToStage.groovy')
  }

  @Test
  void matchingApprovalPromotesTheSameDigestToProdAndLatest() {
    callbacks.registerBefore('prod:promotion') { dsl.approveImmutableImage(reference) }
    promote()
    def imports = commands.findAll { it.contains('acr import ') }
    assertThat(imports).hasSize(2)
    assertThat(imports.every { it.contains("--source ${reference}") }).isTrue()
    assertThat(imports[0]).contains('-t xui/webapp:prod-aaaaaaa-20261102120000')
    assertThat(imports[1]).contains('-t xui/webapp:latest')
  }

  @Test
  void failedOrAbsentEvidenceStopsBeforeAnyRegistryCommand() {
    ['FAIL', 'BLOCKED', 'missing receipt', 'pilot', 'wrong candidate', 'interrupted'].each { reason ->
      callbacks.registerBefore('prod:promotion') { throw new IllegalStateException(reason) }
      assertThatThrownBy { promote() }.hasMessage(reason)
      assertThat(commands).isEmpty()
    }
  }

  @Test
  void previousApprovalCannotSurviveAnAbsentCurrentVerification() {
    config.approvedImageReference = reference
    assertThatThrownBy { promote() }.hasMessage('Release evidence has not approved this immutable image')
    assertThat(commands).isEmpty()
  }

  @Test
  void anImageFromAnotherBuildCannotBeRelabelledAsTheCurrentCheckout() {
    config.imageCandidate.producerBuild = '41'
    callbacks.registerBefore('prod:promotion') { dsl.approveImmutableImage(reference) }
    assertThatThrownBy { promote() }.hasMessage('No immutable image belongs to this source and Jenkins build')
    assertThat(commands).isEmpty()
  }

  @Test
  void defaultConsumerRetainsExistingTagBasedPromotion() {
    config.immutableImagePromotion = false
    promote()
    def imports = commands.findAll { it.contains('acr import ') }
    assertThat(imports).hasSize(2)
    assertThat(imports.every { it.contains('--source cnpacr.azurecr.io/xui/webapp:build-aaaaaaa-20261102120000') }).isTrue()
  }

  private void promote() {
    script.call(pipelineCallbacksRunner: new PipelineCallbacksRunner(callbacks), appPipelineConfig: config,
      subscription: 'sandbox', environment: 'prod', product: 'xui', component: 'webapp', stage: DockerImage.DeploymentStage.PROD)
  }
}
