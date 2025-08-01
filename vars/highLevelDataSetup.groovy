#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
  // Handle test environments where params might be simple types
  def pcr = params.pipelineCallbacksRunner
  def config = params.appPipelineConfig
  def builder = params.builder
  def environment = params.environment
  def product = params.product

  // Safety check for test environments
  if (!pcr || pcr instanceof String) {
    echo "Skipping high level data setup - test environment or missing pipelineCallbacksRunner"
    return
  }

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
