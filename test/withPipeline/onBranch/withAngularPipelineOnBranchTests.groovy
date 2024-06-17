package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.*
import withPipeline.BaseCnpPipelineTest

class withAngularPipelineOnBranchTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnBranchTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(AngularBuilder)
    stubBuilder.demand.setupToolVersion(1) {}
    stubBuilder.demand.build(0) {}

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

