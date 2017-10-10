import org.junit.Before

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource
import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Test

class withPipelineTest extends BasePipelineTest {

  String sharedLibs = new File("/Users/neilri/dev/git/contino/moj-jenkins-library")

  @Override
  @Before
  void setUp() {

    super.setUp()
  }

  @Test
  void test1() {
    def library = library()
      .name('Infrastructure')
      .targetPath(sharedLibs)
      .retriever(projectSource(sharedLibs))
      .defaultVersion("master")
      .allowOverride(true)
      .implicit(false)
      .build()
    helper.registerSharedLibrary(library)
    helper.registerAllowedMethod("deleteDir", {})
    loadScript("test/examplePipeline.jenkins")
    printCallStack()
  }

}
