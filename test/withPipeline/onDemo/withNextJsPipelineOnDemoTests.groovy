package withPipeline.onDemo

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.NextJsBuilder
import withPipeline.BaseCnpPipelineTest

class withNextJsPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNextJsPipeline.jenkins"

  withNextJsPipelineOnDemoTests() {
    super("demo", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(NextJsBuilder)
//    todo: revisit these demands
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      techStackMaintenance(0) {}
      sonarScan(0) {}
      smokeTest(0) {} //demo
      functionalTest(0) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}
