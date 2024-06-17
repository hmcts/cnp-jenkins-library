package withInfraPipeline.onDemo

import org.junit.Test
import withPipeline.BaseCnpPipelineTest


class withInfraPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleInfraPipeline.jenkins"

  withInfraPipelineOnDemoTests() {
    super("demo", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    runScript("testResources/$jenkinsFile")
  }
}

