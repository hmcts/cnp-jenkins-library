package withPipeline.onPreview

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.YarnBuilder
import withPipeline.BaseCnpPipelineTest

class withNodeJsPipelineOnPreviewTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipeline.jenkins"

  withNodeJsPipelineOnPreviewTests() {
    super("PR-999", jenkinsFile)
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
      smokeTest(1) {} //preview-staging
      functionalTest(1) {}
    }

    binding.getVariable('env').putAt('CHANGE_URL', 'http://github.com/some-repo/pr/16')
    binding.getVariable('env').putAt('CHANGE_TITLE', 'Some change')
    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

