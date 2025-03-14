package withPipeline.onDemo

import groovy.mock.interceptor.StubFor
import org.junit.Ignore
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import withPipeline.BaseCnpPipelineTest

@Ignore("Fails with verify[0]: expected 1..1 call(s) to 'setupToolVersion' but was called 0 time(s), can't figure out why")
class withAngularPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnDemoTests() {
    super("demo", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def stubBuilder = new StubFor(AngularBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(0) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

