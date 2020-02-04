package withPipeline.onBranch

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import uk.gov.hmcts.contino.GradleBuilder
import uk.gov.hmcts.contino.JavaDeployer
import uk.gov.hmcts.contino.MockJenkinsPlugin
import uk.gov.hmcts.contino.MockJenkinsPluginManager
import withPipeline.BaseCnpPipelineTest

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource
import com.lesfurets.jenkins.unit.BasePipelineTest
import uk.gov.hmcts.contino.MockJenkins
import org.junit.Test

class withJavaPipelineOnBranchTests extends BaseCnpPipelineTest {

  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withJavaPipelineOnBranchTests() {
    super("feature-branch", jenkinsFile)
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

