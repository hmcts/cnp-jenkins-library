package withNightlyPipeline.onMaster

import org.junit.Test
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

    // Mock the AngularBuilder constructor to return a mock instance
    def mockAngularBuilder = [
      setupToolVersion: {},
      build: {},
      securityCheck: {},
      crossBrowserTest: { String browserName = null -> }, // Handle both parameterless and parameterized calls
      performanceTest: {},
      mutationTest: {},
      fullFunctionalTest: {},
      asBoolean: { true }
    ]

    // Register a method to intercept AngularBuilder constructor calls
    helper.registerAllowedMethod("AngularBuilder", [Object.class], { steps ->
      return mockAngularBuilder
    })

    // Also register as a constructor call
    binding.setVariable('AngularBuilder', { steps -> mockAngularBuilder })

    runScript("testResources/$jenkinsFile")
  }
}
