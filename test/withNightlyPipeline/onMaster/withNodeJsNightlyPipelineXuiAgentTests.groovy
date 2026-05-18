package withNightlyPipeline.onMaster

import org.junit.Test
import withPipeline.BaseCnpPipelineTest

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.assertj.core.api.Assertions.assertThat

class withNodeJsNightlyPipelineXuiAgentTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsNightlyPipelineForXuiAgent.jenkins"

  withNodeJsNightlyPipelineXuiAgentTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineUsesXuiAgentWithoutDailyLabel() {
    helper.registerAllowedMethod("setupToolVersion", [], {})
    helper.registerAllowedMethod("build", [], {})
    helper.registerAllowedMethod("securityCheck", [], {})
    helper.registerAllowedMethod("e2eTest", [], {})
    helper.registerAllowedMethod("fullFunctionalTest", [], {})

    runScript("testResources/$jenkinsFile")

    def nodeCalls = helper.callStack.findAll { call ->
      call.methodName == "node"
    }

    assertThat(nodeCalls.any { call ->
      callArgsToString(call).contains("xui")
    }).isTrue()
    assertThat(nodeCalls.any { call ->
      callArgsToString(call).contains("xui && daily")
    }).isFalse()
  }
}
