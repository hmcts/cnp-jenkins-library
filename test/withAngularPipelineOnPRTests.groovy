import groovy.mock.interceptor.MockFor
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import uk.gov.hmcts.contino.NodeDeployer
import uk.gov.hmcts.contino.StaticSiteDeployer
import uk.gov.hmcts.contino.YarnBuilder

class withAngularPipelineOnPRTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnPRTests() {
    super("PR-999", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def mockBuilder = new MockFor(AngularBuilder)
    mockBuilder.demand.with {
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //preview-staging
      functionalTest(1) {}
      smokeTest(1) {} // preview-prod
    }

    def mockDeployer = new MockFor(StaticSiteDeployer)
    mockDeployer.ignore.getServiceUrl() { env, slot -> return null} // we don't care when or how often this is called
    mockDeployer.demand.with {
      // preview-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // preview-prod
      healthCheck() { env, slot -> return null }
    }

    mockBuilder.use {
      mockDeployer.use {
        runScript("testResources/$jenkinsFile")
        printCallStack()
      }
    }
  }
}

