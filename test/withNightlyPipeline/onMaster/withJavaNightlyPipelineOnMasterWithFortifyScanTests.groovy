package withNightlyPipeline.onMaster

import groovy.mock.interceptor.MockFor
import org.junit.Ignore
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

@Ignore("Could not figure out a null 'currentBuild' is injected into notifyBuildFixed.groovy as a result an Exception is thrown")
class withJavaNightlyPipelineOnMasterWithFortifyScanTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaNightlyPipelineWithFortifyScan.jenkins"

    withJavaNightlyPipelineOnMasterWithFortifyScanTests() {
    super("master", jenkinsFile)
  }

  @Test
  void NightlyPipelineExecutesExpectedStepsInExpectedOrder() {
    def mockBuilder = new MockFor(GradleBuilder)
    mockBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      securityCheck(1) {}
      fortifyScan(1) {}
      crossBrowserTest(0) {}
      performanceTest(1) {}
      mutationTest(1){}
      fullFunctionalTest(1){}
    }

    mockBuilder.use {
        runScript("testResources/$jenkinsFile")
    }
  }
}

