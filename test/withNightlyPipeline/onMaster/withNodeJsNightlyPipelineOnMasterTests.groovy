package withNightlyPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

class withNodeJsNightlyPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsNightlyPipeline.jenkins"

  withNodeJsNightlyPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      securityCheck(1) {}
      crossBrowserTest(5) {}  // Includes parallelCrossBrowserTest
      performanceTest(1) {}
      mutationTest(1){}
      fullFunctionalTest(1){}
      asBoolean() { return true } // Add asBoolean method expectation
    }

    stubBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}
