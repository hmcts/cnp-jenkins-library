import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class sectionDeployToAKSTest extends BasePipelineTest {

  def script

  @Override
  @Before
  void setUp() {
    super.setUp()
    script = loadScript('vars/sectionDeployToAKS.groovy')
  }

  @Test
  void skipsFinalHelmUninstallOnMasterWhenHelmOnMasterIsEnabled() {
    assertThat(script.shouldUninstallHelmRelease(true, true, false)).isFalse()
  }

  @Test
  void uninstallsFinalHelmReleaseOnMasterWhenHelmOnMasterIsDisabled() {
    assertThat(script.shouldUninstallHelmRelease(true, false, true)).isTrue()
  }

  @Test
  void skipsFinalHelmUninstallOffMasterWhenKeepHelmLabelIsPresent() {
    assertThat(script.shouldUninstallHelmRelease(false, false, true)).isFalse()
  }

  @Test
  void uninstallsFinalHelmReleaseOffMasterWhenKeepHelmLabelIsMissing() {
    assertThat(script.shouldUninstallHelmRelease(false, true, false)).isTrue()
  }
}
