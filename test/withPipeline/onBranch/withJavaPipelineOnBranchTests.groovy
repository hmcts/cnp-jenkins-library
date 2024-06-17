package withPipeline.onBranch


import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

class withJavaPipelineOnBranchTests extends BaseCnpPipelineTest {

  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withJavaPipelineOnBranchTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.setupToolVersion(1) {}
    stubBuilder.demand.build(0) {}

    stubBuilder.use {
      runScript("testResources/exampleJavaPipeline.jenkins")
    }

    stubBuilder.expect.verify()
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.setupToolVersion(0) {}
    stubBuilder.demand.build(0) {}
    stubBuilder.demand.test(0) {}
    stubBuilder.demand.securityCheck(0) {}
    stubBuilder.demand.sonarScan(0) {}

    stubBuilder.use {
        runScript("testResources/exampleJavaPipeline.jenkins")
    }

    stubBuilder.expect.verify()
  }

}

