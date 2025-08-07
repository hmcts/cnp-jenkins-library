package withNightlyPipeline.onMaster

import groovy.mock.interceptor.StubFor
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
    // Register pipeline DSL methods for SauceConnect if needed
    helper.registerAllowedMethod("saucePublisher", [], {})
    helper.registerAllowedMethod("withSauceConnect", [String.class, Closure.class], { String tunnelName, Closure closure -> closure.call() })
    helper.registerAllowedMethod("sauce", [String.class, Closure.class], { String sauceId, Closure closure -> closure.call() })
    helper.registerAllowedMethod("sauceconnect", [Map.class, Closure.class], { Map options, Closure closure -> closure.call() })

    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0..10) {}
      build(0..10) {}
      securityCheck(0..10) {}
      crossBrowserTest(0..10) {}
      performanceTest(0..10) {}
      mutationTest(0..10) {}
      fullFunctionalTest(0..10) {}
      asBoolean(0..10) { return true }
    }

    stubBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}
