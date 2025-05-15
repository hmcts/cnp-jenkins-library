package withInfraPipeline.onMaster

import org.junit.Ignore
import org.junit.Test
import withPipeline.BaseCnpPipelineTest

@Ignore("Could not figure out a null 'currentBuild' is injected into notifyBuildFixed.groovy as a result an Exception is thrown")
class withInfraPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleInfraPipeline.jenkins"

  withInfraPipelineOnMasterTests() {
    super("master", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    runScript("testResources/$jenkinsFile")
  }
}

