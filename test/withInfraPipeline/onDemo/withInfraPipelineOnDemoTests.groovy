package withInfraPipeline.onDemo

import org.junit.Test
import withPipeline.BaseCnpPipelineTest

class withInfraPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleInfraPipeline.jenkins"

  withInfraPipelineOnDemoTests() {
    super("demo", jenkinsFile)
    binding.env.ARM_SUBSCRIPTION_ID = 'target-subscription-id'
    binding.env.JENKINS_SUBSCRIPTION_ID = 'jenkins-subscription-id'
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    runScript("testResources/$jenkinsFile")
  }
}

