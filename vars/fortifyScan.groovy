#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  Builder builder = params.builder

  def environment = params.environment
  def product = params.product

  if (config.fortifyScan) {
    stageWithAgent("Fortify Scan", product) {
      withFortifySecrets(product, environment) {
        pcr.callAround('fortifyscan') {
          builder.fortifyScan()
        }
      }
    }
  }
}
