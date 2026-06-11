package withPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.PythonBuilder
import withPipeline.BaseCnpPipelineTest

class withPythonPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "examplePythonPipeline.jenkins"

  withPythonPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      techStackMaintenance(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //aat-staging
      e2eTest(1) {}
      functionalTest(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      sonarScan(0) {}
      smokeTest(0) {}
      functionalTest(0) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}
