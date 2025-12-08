package withInfraPipeline.onMaster

import org.junit.Test
import withPipeline.BaseCnpPipelineTest

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
