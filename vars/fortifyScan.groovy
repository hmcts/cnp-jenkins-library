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
      warnError(message: 'Failure in Fortify Scan', stageResult: 'UNSTABLE') {
        pcr.callAround('fortify-scan') {
          builder.fortifyScan()
        }
      }

      warnError(message: 'Failure in Fortify vulnerability report', stageResult: 'UNSTABLE') {
        fortifyVulnerabilityReport()
      }

      archiveArtifacts allowEmptyArchive: true, artifacts: 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*'
    }
  }
}
