package withNightlyPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import withPipeline.BaseCnpPipelineTest

class withAngularNightlyPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularNightlyPipeline.jenkins"

  withAngularNightlyPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(AngularBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      securityCheck(1) {}
      crossBrowserTest(5) {}  // Includes parallelCrossBrowserTest
      performanceTest(1) {}
      mutationTest(1){}
      fullFunctionalTest(1){}
      asBoolean() { return true } // Add missing asBoolean method expectation
    }

    stubBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}
