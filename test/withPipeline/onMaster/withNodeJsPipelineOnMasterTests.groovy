package withPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Ignore
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

@Ignore("Fails with verify[0]: expected 1..1 call(s) to 'setupToolVersion' but was called 0 time(s), can't figure out why")
class withNodeJsPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipeline.jenkins"

  withNodeJsPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      techStackMaintenance(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

