package withPipeline.onMaster

import groovy.mock.interceptor.MockFor
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import uk.gov.hmcts.contino.NodeDeployer
import uk.gov.hmcts.contino.StaticSiteDeployer
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

class withAngularPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def mockBuilder = new MockFor(AngularBuilder)
    mockBuilder.demand.with {
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
      smokeTest(3) {} // aat-prod, prod-staging, prod-prod
    }

    def mockDeployer = new MockFor(StaticSiteDeployer)
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

    mockBuilder.use {
      mockDeployer.use {
        runScript("testResources/$jenkinsFile")
      }
    }
  }
}

