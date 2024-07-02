#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  def builder = params.builder

  def product = params.product
  def fortifyVaultName = params.fortifyVaultName

  stageWithAgent("Fortify Scan", product) {
    withFortifySecrets(fortifyVaultName) {
      warnError('Failure in Fortify Scan') {
        pcr.callAround('fortify-scan') {
          builder.fortifyScan()
        }
      }
    }
  }
}
