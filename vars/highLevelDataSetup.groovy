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

  if (config.highLevelDataSetup) {
    def highLevelDataSetupKeyVaultName = config.highLevelDataSetupKeyVaultName

    stageWithAgent("High Level Data Setup - ${environment}", product) {
        def vaultName = !highLevelDataSetupKeyVaultName?.trim() ? product : highLevelDataSetupKeyVaultName

        withDefinitionImportSecretsAndEnvVars(vaultName, environment, config.vaultEnvironmentOverrides){
        pcr.callAround('highleveldatasetup') {
          builder.highLevelDataSetup(environment)
        }
      }
    }
  }
}
