package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.NextJsBuilder
import withPipeline.BaseCnpPipelineTest

class withNextJsPipelineOnBranchTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNextJsPipeline.jenkins"

  withNextJsPipelineOnBranchTests() {
    super("test-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(NextJsBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      techStackMaintenance(0) {}
      sonarScan(0) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}
