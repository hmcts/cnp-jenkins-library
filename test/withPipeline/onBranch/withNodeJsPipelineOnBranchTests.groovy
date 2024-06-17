package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

class withNodeJsPipelineOnBranchTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipeline.jenkins"

  withNodeJsPipelineOnBranchTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.setupToolVersion(1) {}
    stubBuilder.demand.build(0) {}

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

