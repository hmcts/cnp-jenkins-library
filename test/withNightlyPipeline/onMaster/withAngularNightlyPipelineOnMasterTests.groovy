package withNightlyPipeline.onMaster

import groovy.mock.interceptor.MockFor
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
    // Register the saucePublisher method for the test environment
    helper.registerAllowedMethod("saucePublisher", [], {})
    // Register the withSauceConnect method for the test environment
    helper.registerAllowedMethod("withSauceConnect", [String.class, Closure.class], { String tunnelName, Closure closure -> closure.call() })
    // Register the sauce method that withSauceConnect calls internally
    helper.registerAllowedMethod("sauce", [String.class, Closure.class], { String sauceId, Closure closure -> closure.call() })
    // Register the sauceconnect method that sauce calls internally
    helper.registerAllowedMethod("sauceconnect", [Map.class, Closure.class], { Map options, Closure closure -> closure.call() })

    def mockBuilder = new MockFor(AngularBuilder)
    mockBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      securityCheck(1) {}
      // Handle parameterless crossBrowserTest calls
      crossBrowserTest(0..5) {}
      // Handle parameterized crossBrowserTest calls with browser names
      crossBrowserTest(0..10) { String browserName -> }
      performanceTest(1) {}
      mutationTest(1){}
      fullFunctionalTest(1){}
      asBoolean() { return true }
    }

    mockBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}
