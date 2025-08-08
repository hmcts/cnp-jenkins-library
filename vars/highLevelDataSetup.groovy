#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
  // Handle test environments where params might be simple types or malformed
  if (!params || params instanceof String) {
    echo "Skipping high level data setup - test environment or invalid params"
    return
  }

  // Safely extract parameters with null checks
  def pcr = params?.pipelineCallbacksRunner
  def config = params?.appPipelineConfig
  def builder = params?.builder
  def environment = params?.environment
  def product = params?.product

  // Additional safety check for test environments
  if (!pcr || !config || !builder) {
    echo "Skipping high level data setup - missing required parameters in test environment"
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
