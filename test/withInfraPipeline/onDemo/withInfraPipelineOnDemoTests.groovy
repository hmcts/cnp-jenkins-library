package withInfraPipeline.onDemo

import org.junit.Ignore
import org.junit.Test
import withPipeline.BaseCnpPipelineTest

@Ignore("Could not figure out a null 'currentBuild' is injected into notifyBuildFixed.groovy as a result an Exception is thrown")
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

