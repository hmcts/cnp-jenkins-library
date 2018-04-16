import org.junit.Before

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static uk.gov.hmcts.contino.ProjectSource.projectSource
import static org.assertj.core.api.Assertions.*
import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Test

class folderExistsTest extends BasePipelineTest {

  // get the 'project' directory
  String projectDir = (new File(this.getClass().getResource("examplePipeline.jenkins").toURI())).parentFile.parentFile.parentFile.parentFile

  @Override
  @Before
  void setUp() {
    super.setUp()

    def library = library()
      .name('Infrastructure')
      .targetPath(projectDir)
      .retriever(projectSource(projectDir))
      .defaultVersion("master")
      .allowOverride(true)
      .implicit(false)
      .build()
    helper.registerSharedLibrary(library)
    helper.registerAllowedMethod('sh', [Map.class], { m -> return projectDir })
    helper.registerAllowedMethod( 'fileExists', [String.class], { m -> return m.contains('testResources') })
    runScript("testResources/folderExistsTestPipeline.jenkins")
    printCallStack()
  }

  @Test
  void testBlockIsCalledIfDirectoryExists() {
    assertThat(helper.callStack.findAll { call ->
        call.methodName == "echo"
    }.any { call ->
        callArgsToString(call).contains("OK - Folder exists")
    }).isTrue()
  }

  @Test
  void testBlockIsNotCalledIfDirectoryDoesntExist() {
    assertThat(helper.callStack.findAll { call ->
        call.methodName == "echo"
    }.any { call ->
        callArgsToString(call).contains("ERROR - Folder doesn't exist")
    }).isFalse()
  }

}
