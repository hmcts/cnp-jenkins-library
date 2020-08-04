#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  Long deploymentNumber

  Builder builder = pipelineType.builder
  def tfOutput
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, subscription )
  approvedEnvironmentRepository(environment, metricsPublisher) {
    lock(resource: "${product}-${component}-${environment}-deploy", inversePrecedence: true) {
      folderExists('infrastructure') {
        stageWithAgent("Build Infrastructure - ${environment}", product) {
          onPreview {
            deploymentNumber = githubCreateDeployment()
          }

          withSubscription(subscription) {
            dir('infrastructure') {
              pcr.callAround("buildinfra:${environment}") {
                timeoutWithMsg(time: 120, unit: 'MINUTES', action: "buildinfra:${environment}") {
                  def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                  withEnv(additionalInfrastructureVariables) {
                    tfOutput = spinInfra(product, component, environment, false, subscription)
                  }
                }
              }
            }

            registerDns(params)

            if (config.migrateDb) {
              stageWithAgent("DB Migration - ${environment}", product) {
                pcr.callAround("dbmigrate:${environment}") {
                  if (tfOutput?.microserviceName) {
                    WarningCollector.addPipelineWarning("deprecated_microservice_name_outputted", "Please remove microserviceName from your terraform outputs, if you are not outputting the microservice name (component) and instead outputting something else you will need to migrate the secrets first, example PR: https://github.com/hmcts/ccd-data-store-api/pull/540"
      , new Date().parse("dd.MM.yyyy", "05.09.2019"))
                  }

                  builder.dbMigrate(
                    tfOutput?.vaultName ? tfOutput.vaultName.value : "${config.dbMigrationVaultName}-${environment}",
                    tfOutput?.microserviceName ? tfOutput.microserviceName.value : component
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
