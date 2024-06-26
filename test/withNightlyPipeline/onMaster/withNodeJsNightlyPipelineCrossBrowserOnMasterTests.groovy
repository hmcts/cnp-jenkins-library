package withNightlyPipeline.onMaster

import groovy.mock.interceptor.MockFor
import org.junit.Ignore
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

@Ignore("java.lang.StackOverflowError at ConcurrentHashMap.java:1541")
class withNodeJsNightlyPipelineCrossBrowserOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsNightlyPipelineForCrossBrowser.jenkins"

  withNodeJsNightlyPipelineCrossBrowserOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def mockBuilder = new MockFor(YarnBuilder)
    mockBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      securityCheck(1) {}
      crossBrowserTest(5) {}  // Includes parallelCrossBrowserTest
      performanceTest(0) {}
      mutationTest(1){}
    }

    mockBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}

