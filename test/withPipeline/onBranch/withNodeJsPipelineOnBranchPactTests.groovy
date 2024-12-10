package withPipeline.onBranch

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.*
import withPipeline.BaseCnpPipelineTest

class withNodeJsPipelineOnBranchPactTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipelineForPact.jenkins"

  withNodeJsPipelineOnBranchPactTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(YarnBuilder)
    def stubPactBroker = new StubFor(PactBroker)

    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
    }

    stubPactBroker.demand.with {
      canIDeploy(0) { version -> return null }
    }


    stubBuilder.use {
      stubPactBroker.use {
        runScript("testResources/$jenkinsFile")
      }
    }

    stubBuilder.expect.verify()
    stubPactBroker.expect.verify()
  }
}

