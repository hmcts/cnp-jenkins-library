import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class azureEvidenceUploadTest extends BasePipelineTest {
  def script
  def commands = []

  @Override
  @Before
  void setUp() {
    super.setUp()
    helper.registerAllowedMethod('withSubscriptionLogin', [String.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('sh', [String.class], { commands << it })
    script = loadScript('vars/azureEvidenceUpload.groovy')
  }

  @Test
  void uploadsWithManagedIdentityAndNoStorageKey() {
    script.call('aat', 'xuicireportsaat01', 'functional-output/blob-evidence', 'reports/90d/xui')

    assertThat(commands).hasSize(1)
    assertThat(commands[0]).contains('AZCOPY_AUTO_LOGIN_TYPE=MSI')
    assertThat(commands[0]).contains('--recursive=true')
    assertThat(commands[0]).doesNotContain('STORAGE_ACCOUNT_KEY', 'withCredentials')
  }
}
