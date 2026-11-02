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
      setupToolVersion(0..1) {}
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      techStackMaintenance(1) {}
      sonarScan(1) {}
      smokeTest(0) {} //aat-staging
      e2eTest(0) {}
      functionalTest(0) {}
    }

    Script pipeline = loadScript("testResources/$jenkinsFile")
    def sectionBuildAndTest = binding.getVariable('sectionBuildAndTest')
    // The library can resolve its own copy of AngularBuilder, which StubFor cannot intercept.
    helper.registerAllowedMethod('sectionBuildAndTest', [Object.class], { params ->
      sectionBuildAndTest.call(params + [builder: new AngularBuilder(delegate)])
    })

    stubBuilder.use {
      runScript(pipeline)
    }

    stubBuilder.expect.verify()
  }
}

