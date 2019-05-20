package withPipeline.onMaster

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import uk.gov.hmcts.contino.PactBroker
import uk.gov.hmcts.contino.NodeDeployer
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
      runConsumerTests(1) { url, version -> return null }
      runProviderVerification(1) { url, version -> return null }
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
      smokeTest(3) {} // aat-prod, prod-staging, prod-prod
    }

    stubPactBroker.demand.with {
      canIDeploy(1) { version -> return null }
    }

    def mockDeployer = new MockFor(NodeDeployer)
    mockDeployer.ignore.getServiceUrl() { env, slot -> return null} // we don't care when or how often this is called
    mockDeployer.demand.with {
      // aat-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // aat-prod
      healthCheck() { env, slot -> return null }
      // prod-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // prod-prod
      healthCheck() { env, slot -> return null }
    }

    stubBuilder.use {
      stubPactBroker.use {
        mockDeployer.use {
          runScript("testResources/$jenkinsFile")
        }
      }
    }

    stubBuilder.expect.verify()
    stubPactBroker.expect.verify()
  }
}
