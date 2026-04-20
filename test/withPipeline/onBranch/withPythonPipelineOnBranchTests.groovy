package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.PythonBuilder
import withPipeline.BaseCnpPipelineTest

class withPythonPipelineOnBranchTests extends BaseCnpPipelineTest {

  final static jenkinsFile = "examplePythonPipeline.jenkins"

  withPythonPipelineOnBranchTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.setupToolVersion(0) {}
    stubBuilder.demand.build(0) {}

    stubBuilder.use {
      runScript("testResources/examplePythonPipeline.jenkins")
    }

    stubBuilder.expect.verify()
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.setupToolVersion(0) {}
    stubBuilder.demand.build(0) {}
    stubBuilder.demand.test(0) {}
    stubBuilder.demand.securityCheck(0) {}
    stubBuilder.demand.sonarScan(0) {}

    stubBuilder.use {
      runScript("testResources/examplePythonPipeline.jenkins")
    }

    stubBuilder.expect.verify()
  }
}
