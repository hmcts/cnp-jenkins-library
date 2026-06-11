package withPipeline.onPreview

import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.PythonBuilder
import withPipeline.BaseCnpPipelineTest

class withPythonPipelineOnPreviewTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "examplePythonPipeline.jenkins"

  withPythonPipelineOnPreviewTests() {
    super("PR-999", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def stubBuilder = new StubFor(PythonBuilder)
    stubBuilder.demand.with {
      setupToolVersion(0) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      techStackMaintenance(0) {}
      sonarScan(0) {}
      smokeTest(0) {} //preview-staging
      e2eTest(0) {}
      functionalTest(0) {}
    }

    binding.getVariable('env').putAt('CHANGE_URL', 'http://github.com/some-repo/pr/16')
    binding.getVariable('env').putAt('CHANGE_TITLE', 'Some change')

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}
