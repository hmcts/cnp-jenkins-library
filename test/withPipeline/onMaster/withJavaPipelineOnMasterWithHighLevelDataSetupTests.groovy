package withPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

class withJavaPipelineOnMasterWithHighLevelDataSetupTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipelineWithHighLevelDataSetup.jenkins"

  withJavaPipelineOnMasterWithHighLevelDataSetupTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      highLevelDataSetup(2) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      sonarScan(0) {}
      highLevelDataSetup(2) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

