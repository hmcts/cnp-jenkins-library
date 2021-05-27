#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  Builder builder = params.builder
  DockerImage.DeploymentStage deploymentStage = params.stage
  def product = params.product

  def dataSetupEnvironment = deploymentStage.label

  if (config.highLevelDataSetup) {
    stageWithAgent("High Level Data Setup - ${dataSetupEnvironment.toUpperCase()}", product) {
      pcr.callAround('highleveldatasetup') {
        builder.highLevelDataSetup(dataSetupEnvironment)
      }
    }
  }
}
