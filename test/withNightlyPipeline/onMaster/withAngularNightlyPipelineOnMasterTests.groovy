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

    // Register all AngularBuilder/YarnBuilder methods with helper to avoid StubFor conflicts
    helper.registerAllowedMethod("setupToolVersion", [], {})
    helper.registerAllowedMethod("build", [], {})
    helper.registerAllowedMethod("securityCheck", [], {})
    helper.registerAllowedMethod("crossBrowserTest", [], {})
    helper.registerAllowedMethod("crossBrowserTest", [String.class], { String browser -> })
    helper.registerAllowedMethod("performanceTest", [], {})
    helper.registerAllowedMethod("mutationTest", [], {})
    helper.registerAllowedMethod("e2eTest", [], {})
    helper.registerAllowedMethod("fullFunctionalTest", [], {})

    runScript("testResources/$jenkinsFile")
  }
}
