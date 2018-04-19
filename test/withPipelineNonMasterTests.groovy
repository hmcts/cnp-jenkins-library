import groovy.mock.interceptor.MockFor
import uk.gov.hmcts.contino.GradleBuilder
import uk.gov.hmcts.contino.MockJenkinsPlugin
import uk.gov.hmcts.contino.MockJenkinsPluginManager

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource
import com.lesfurets.jenkins.unit.BasePipelineTest
import uk.gov.hmcts.contino.MockJenkins
import org.junit.Test

class withPipelineNonMasterTests extends BasePipelineTest {

  // get the 'project' directory
  String projectDir = (new File(this.getClass().getResource("examplePipeline.jenkins").toURI())).parentFile.parentFile.parentFile.parentFile

  withPipelineNonMasterTests() {
    super.setUp()
    binding.setVariable("scm", null)
    binding.setVariable("env", [BRANCH_NAME: "feature-branch"])
    binding.setVariable("Jenkins", [instance: new MockJenkins(new MockJenkinsPluginManager([new MockJenkinsPlugin('sonar', true) ] as MockJenkinsPlugin[]))])

    def library = library()
      .name('Infrastructure')
      .targetPath(projectDir)
      .retriever(projectSource(projectDir))
      .defaultVersion("master")
      .allowOverride(true)
      .implicit(false)
      .build()
    helper.registerSharedLibrary(library)
    helper.registerAllowedMethod("deleteDir", {})
    helper.registerAllowedMethod("stash", [Map.class], {})
    helper.registerAllowedMethod("unstash", [String.class], {})
    helper.registerAllowedMethod("withEnv", [List.class, Closure.class], {})
    helper.registerAllowedMethod("ansiColor", [String.class, Closure.class], { s, c -> c.call() })
    helper.registerAllowedMethod("withCredentials", [LinkedHashMap, Closure.class], { c -> c.call()})
    helper.registerAllowedMethod("azureServicePrincipal", [LinkedHashMap, Closure.class], { c -> c.call()})
    helper.registerAllowedMethod("sh", [Map.class], { return "" })
    helper.registerAllowedMethod('fileExists', [String.class], { c -> true })
    helper.registerAllowedMethod("timestamps", [Closure.class], { c -> c.call() })
    helper.registerAllowedMethod("withSonarQubeEnv", [String.class, Closure.class], { s, c -> c.call() })
    helper.registerAllowedMethod("waitForQualityGate", { [status: 'OK'] })
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def mockBuilder = new MockFor(GradleBuilder)
    mockBuilder.demand.build() {}
    mockBuilder.demand.test() {}
    mockBuilder.demand.securityCheck() {}
    mockBuilder.demand.sonarScan() {}
    mockBuilder.use {
      runScript("testResources/examplePipeline.jenkins")
      printCallStack()
    }
  }
}

