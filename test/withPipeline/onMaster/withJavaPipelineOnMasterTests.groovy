package withPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

class withJavaPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withJavaPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0..1) {}
      build(0..1) {}
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
    // The library can resolve its own copy of GradleBuilder, which StubFor cannot intercept.
    helper.registerAllowedMethod('sectionBuildAndTest', [Object.class], { params ->
      sectionBuildAndTest.call(params + [builder: new GradleBuilder(delegate, params.product)])
    })

    stubBuilder.use {
      runScript(pipeline)
    }

    stubBuilder.expect.verify()
    assert helper.callStack.any { call ->
      call.methodName == 'parallel' && call.args[0] instanceof Map && call.args[0].failFast == true
    }
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0..1) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
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

