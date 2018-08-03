package withNightlyPipeline.onMaster

import groovy.mock.interceptor.MockFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

class withNodeJsNightlyPipelineCrossBrowserOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsNightlyPipelineForCrossBrowser.jenkins"

  withNodeJsNightlyPipelineCrossBrowserOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def mockBuilder = new MockFor(YarnBuilder)
    mockBuilder.demand.with {
      build(1) {}
      securityCheck(1) {}
      crossBrowserTest(1) {}
      mutationTest(1){}
    }

    mockBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}

