package withInfraPipeline.onMaster

import org.junit.Test
import withPipeline.BaseCnpPipelineTest

class withInfraPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleInfraPipeline.jenkins"

  withInfraPipelineOnMasterTests() {
    super("master", jenkinsFile)
    binding.env.ARM_SUBSCRIPTION_ID = 'target-subscription-id'
    binding.env.JENKINS_SUBSCRIPTION_ID = 'jenkins-subscription-id'
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    runScript("testResources/$jenkinsFile")
  }
}
