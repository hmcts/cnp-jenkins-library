#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  Builder builder = params.builder

  def dataSetupEnvironment = params.dataSetupEnvironment
  def product = params.product

  if (config.highLevelDataSetup) {
    stageWithAgent("High Level Data Setup - ${dataSetupEnvironment}", product) {
      pcr.callAround('highleveldatasetup') {
        builder.highLevelDataSetup(dataSetupEnvironment)
      }
    }
  }
}
