package withPipeline.onPreview

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.NodeDeployer
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
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //preview-staging
      functionalTest(1) {}
      smokeTest(1) {} // preview-prod
    }

    binding.getVariable('env').putAt('CHANGE_URL', 'http://github.com/some-repo/pr/16')
    binding.getVariable('env').putAt('CHANGE_TITLE', '[PREVIEW] Some change')
    def mockDeployer = new MockFor(NodeDeployer)
    mockDeployer.ignore.getServiceUrl() { env, slot -> return null} // we don't care when or how often this is called
    mockDeployer.demand.with {
      // preview-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // preview-prod
      healthCheck() { env, slot -> return null }
    }

    stubBuilder.use {
      mockDeployer.use {
        runScript("testResources/$jenkinsFile")
      }
    }

    stubBuilder.expect.verify()
  }
}

