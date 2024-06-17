package withPipeline.onMaster

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import uk.gov.hmcts.contino.PactBroker
import withPipeline.BaseCnpPipelineTest

class withNodeJsPipelinePactOnMaster extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipelineForPact.jenkins"

  withNodeJsPipelinePactOnMaster() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(YarnBuilder)
    def stubPactBroker = new StubFor(PactBroker)

    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      test(1) {}
      sonarScan(1) {}
      securityCheck(1) {}
      techStackMaintenance(1) {}
      runConsumerTests(1) { url, version -> return null }
      runConsumerCanIDeploy(1) {}
      runProviderVerification(1) { url, version, publish -> return null }
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
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
