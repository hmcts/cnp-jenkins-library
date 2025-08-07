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
    // Register pipeline DSL methods
    helper.registerAllowedMethod("saucePublisher", [], {})
    helper.registerAllowedMethod("withSauceConnect", [String.class, Closure.class], { String tunnelName, Closure closure -> closure.call() })
    helper.registerAllowedMethod("sauce", [String.class, Closure.class], { String sauceId, Closure closure -> closure.call() })
    helper.registerAllowedMethod("sauceconnect", [Map.class, Closure.class], { Map options, Closure closure -> closure.call() })

    def stubBuilder = new StubFor(AngularBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      securityCheck(0) {}
      crossBrowserTest(0) {} // Handle parameterless calls
      performanceTest(0) {}
      mutationTest(0) {}
      fullFunctionalTest(0) {}
      asBoolean(0) { return true }
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }
  }
}
