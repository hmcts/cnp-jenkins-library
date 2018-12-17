package withPipeline.onDemo

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import uk.gov.hmcts.contino.JavaDeployer
import withPipeline.BaseCnpPipelineTest

class withJavaPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withJavaPipelineOnDemoTests() {
    super("demo", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //demo-staging
      smokeTest(1) {} // demo-prod
    }

    def mockDeployer = new MockFor(JavaDeployer)
    mockDeployer.ignore.getServiceUrl() { env, slot -> return null} // we don't care when or how often this is called
    mockDeployer.demand.with {
      // demo-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // demo-prod
      healthCheck() { env, slot -> return null }
    }

    stubBuilder.use {
      mockDeployer.use {
        runScript("testResources/$jenkinsFile")
      }
    }

    stubBuilder.expect.verify()
  }
}

