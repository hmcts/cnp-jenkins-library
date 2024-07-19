package withPipeline.onPreview

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

class withJavaPipelineOnPreviewWithHighLevelDataSetupTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipelineWithHighLevelDataSetup.jenkins"

    withJavaPipelineOnPreviewWithHighLevelDataSetupTests() {
    super("PR-999", jenkinsFile)
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
      techStackMaintenance(1) {}
      highLevelDataSetup(1) {}
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

