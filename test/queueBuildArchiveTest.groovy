import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class queueBuildArchiveTest extends BasePipelineTest {

  def queuedBuild
  def queuedBuildCount = 0
  def buildFailure
  def script

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      BUILD_URL: 'https://build.example/job/service/job/PR-1/4/',
      JOB_NAME: 'service/PR-1',
      BUILD_NUMBER: '4'
    ])
    binding.setVariable('currentBuild', [result: 'FAILURE'])
    helper.registerAllowedMethod('string', [Map.class], { it })
    helper.registerAllowedMethod('build', [Map.class], {
      if (buildFailure) {
        throw buildFailure
      }
      queuedBuild = it
      queuedBuildCount++
    })
    helper.registerAllowedMethod('echo', [String.class], {})
    script = loadScript('vars/queueBuildArchive.groovy')
  }

  @Test
  void queuesTheConfiguredArchiveJobWithoutWaiting() {
    script.call(product: 'et', component: 'cos')

    assertThat(queuedBuild.job).isEqualTo('/Archive Completed Builds')
    assertThat(queuedBuild.wait).isFalse()
    assertThat(queuedBuild.propagate).isFalse()
    assertThat(parameter('SOURCE_BUILD_URL')).isEqualTo('https://build.example/job/service/job/PR-1/4/')
    assertThat(parameter('SOURCE_JOB_NAME')).isEqualTo('service/PR-1')
    assertThat(parameter('SOURCE_BUILD_NUMBER')).isEqualTo('4')
    assertThat(parameter('SOURCE_BUILD_RESULT')).isEqualTo('FAILURE')
    assertThat(parameter('SOURCE_PRODUCT')).isEqualTo('et')
    assertThat(parameter('SOURCE_COMPONENT')).isEqualTo('cos')
    assertThat(binding.getVariable('env').BUILD_ARCHIVE_QUEUED).isEqualTo('true')
  }

  @Test
  void queuesOnlyOnceWhenCalledForMultipleRetryAttempts() {
    script.call(product: 'et', component: 'cos')
    script.call(product: 'et', component: 'cos')

    assertThat(queuedBuildCount).isEqualTo(1)
  }

  @Test
  void doesNotQueueFromTheArchiveJobItself() {
    binding.getVariable('env').JOB_NAME = 'Archive Completed Builds'

    script.call(product: 'et', component: 'cos')

    assertThat(queuedBuild).isNull()
  }

  @Test
  void ignoresSuccessfulBuilds() {
    binding.getVariable('currentBuild').result = 'SUCCESS'

    script.call(product: 'et', component: 'cos')

    assertThat(queuedBuild).isNull()
  }

  @Test
  void doesNothingWhenTheBuildUrlIsUnavailable() {
    binding.getVariable('env').BUILD_URL = ''

    script.call(product: 'et', component: 'cos')

    assertThat(queuedBuild).isNull()
  }

  @Test
  void preservesAnInterruptionWhileQueuingTheArchive() {
    def interruption = new FlowInterruptedException(Result.ABORTED, true)
    buildFailure = interruption

    try {
      script.call(product: 'et', component: 'cos')
    } catch (FlowInterruptedException expected) {
      assertThat(expected).isSameAs(interruption)
      return
    }

    throw new AssertionError('Expected the queue interruption to propagate')
  }

  private String parameter(String name) {
    queuedBuild.parameters.find { it.name == name }.value
  }
}
