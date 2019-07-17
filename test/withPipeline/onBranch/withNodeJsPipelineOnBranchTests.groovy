package withPipeline.onBranch

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.*

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource

class withNodeJsPipelineOnBranchTests extends BasePipelineTest {
  final static jenkinsFile = "exampleNodeJsPipeline.jenkins"

  // get the 'project' directory
  String projectDir = (new File(this.getClass().getClassLoader().getResource(jenkinsFile).toURI())).parentFile.parentFile.parentFile.parentFile

  withNodeJsPipelineOnBranchTests() {
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
    helper.registerAllowedMethod("withSonarQubeEnv", [String.class, Closure.class], { s, c -> c.call() })
    helper.registerAllowedMethod("waitForQualityGate", { [status: 'OK'] })
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.setupToolVersion() {}
    stubBuilder.demand.build() {}
    stubBuilder.demand.test() {}
    stubBuilder.demand.securityCheck() {}
    stubBuilder.demand.sonarScan() {}

    // ensure no deployer methods are called
    def mockDeployer = new MockFor(NodeDeployer)

    mockDeployer.use {
      stubBuilder.use {
        runScript("testResources/$jenkinsFile")
      }
    }

    stubBuilder.expect.verify()
  }
}

