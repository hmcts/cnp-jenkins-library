package withNightlyPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

class withJavaNightlyPipelineOnMasterWithFortifyScanTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaNightlyPipelineWithFortifyScan.jenkins"

  withJavaNightlyPipelineOnMasterWithFortifyScanTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      fortifyScan(1) {}
      securityCheck(1) {}
      crossBrowserTest(0) {}
      performanceTest(1) {}
      mutationTest(1) {}
      e2eTest(1) {}
      fullFunctionalTest(1){}
      asBoolean() { return true } // Add missing asBoolean method expectation
    }

    stubBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}
