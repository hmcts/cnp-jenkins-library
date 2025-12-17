package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.PythonBuilder
import withPipeline.BaseCnpPipelineTest

class withPythonPipelineOnBranchTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "examplePythonPipeline.jenkins"

  withPythonPipelineOnBranchTests() {
    super("feature/test-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      techStackMaintenance(1) {}
      sonarScan(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }
  }
}
