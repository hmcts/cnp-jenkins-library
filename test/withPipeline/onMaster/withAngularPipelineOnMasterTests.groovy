package withPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import withPipeline.BaseCnpPipelineTest

class withAngularPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(AngularBuilder)
//    todo: revisit these demands
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      techStackMaintenance(0) {}
      sonarScan(0) {}
      smokeTest(0) {} //aat-staging
      functionalTest(0) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

