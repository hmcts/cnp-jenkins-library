package withPipeline.onDemo

import groovy.mock.interceptor.StubFor
import org.junit.Ignore
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

@Ignore("java.lang.StackOverflowError at ClosureMetaClass.java:192")
class withNodeJsPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipeline.jenkins"

  withNodeJsPipelineOnDemoTests() {
    super("demo", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def stubBuilder = new StubFor(YarnBuilder)
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

