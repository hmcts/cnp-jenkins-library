package withNightlyPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.PythonBuilder
import withPipeline.BaseCnpPipelineTest

class withPythonNightlyPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "examplePythonNightlyPipeline.jenkins"

  withPythonNightlyPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      securityCheck(1) {}
      crossBrowserTest(0) {}
      performanceTest(1) {}
      e2eTest(1) {}
      fullFunctionalTest(1) {}
      asBoolean() { return true }
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }
  }
}
