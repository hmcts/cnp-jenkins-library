package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

class withNodeJsPipelineOnBranchPactTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipelineForPact.jenkins"

  withNodeJsPipelineOnBranchPactTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(0) {}
      runConsumerTests(1) {}
      runConsumerCanIDeploy(1) {}
      asBoolean() { return true }
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }
  }
}
