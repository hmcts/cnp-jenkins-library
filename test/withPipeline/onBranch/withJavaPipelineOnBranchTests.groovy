package withPipeline.onBranch

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import uk.gov.hmcts.contino.GradleBuilder
import uk.gov.hmcts.contino.JavaDeployer
import uk.gov.hmcts.contino.MockJenkinsPlugin
import uk.gov.hmcts.contino.MockJenkinsPluginManager

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource
import com.lesfurets.jenkins.unit.BasePipelineTest
import uk.gov.hmcts.contino.MockJenkins
import org.junit.Test

class withJavaPipelineOnBranchTests extends BasePipelineTest {

  // get the 'project' directory
  String projectDir = (new File(this.getClass().getClassLoader().getResource("exampleJavaPipeline.jenkins").toURI())).parentFile.parentFile.parentFile.parentFile

  withJavaPipelineOnBranchTests() {
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
    helper.registerAllowedMethod("checkout", [Class.class], { return [GIT_BRANCH: "pr-47", GIT_COMMIT:"379c53a716b92cf79439db07edac01ba1028535d", GIT_URL:"https://github.com/HMCTS/custard-backend.git"] })
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.setupToolVersion() {}
    stubBuilder.demand.build() {}
    stubBuilder.demand.test() {}
    stubBuilder.demand.securityCheck() {}
    stubBuilder.demand.sonarScan() {}

    // ensure no deployer methods are called
    def mockDeployer = new MockFor(JavaDeployer)

    mockDeployer.use {
      stubBuilder.use {
        runScript("testResources/exampleJavaPipeline.jenkins")
      }
    }

    stubBuilder.expect.verify()
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.setupToolVersion(1) {}
    stubBuilder.demand.build(0) {}
    stubBuilder.demand.test(0) {}
    stubBuilder.demand.securityCheck(0) {}
    stubBuilder.demand.sonarScan(0) {}

    // ensure no deployer methods are called
    def mockDeployer = new MockFor(JavaDeployer)

    mockDeployer.use {
      stubBuilder.use {
          runScript("testResources/exampleJavaPipeline.jenkins")
      }
    }

    stubBuilder.expect.verify()
  }

}

