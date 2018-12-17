package withPipeline.onPreview

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import uk.gov.hmcts.contino.StaticSiteDeployer
import withPipeline.BaseCnpPipelineTest

class withAngularPipelineOnPreviewTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnPreviewTests() {
    super("PR-999", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def stubBuilder = new StubFor(AngularBuilder)
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
    def mockDeployer = new MockFor(StaticSiteDeployer)
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

