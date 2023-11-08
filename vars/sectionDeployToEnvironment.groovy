#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.MetricsPublisher

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def aksSubscription = params.aksSubscription
  def product = params.product
  def component = params.component
  def tfPlanOnly = params.tfPlanOnly
  def expires = params.expires
  Long deploymentNumber

  Builder builder = pipelineType.builder
  def tfOutput
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  approvedEnvironmentRepository(environment, metricsPublisher) {
    lock(resource: "${product}-${component}-${environment}-deploy", inversePrecedence: true) {
      folderExists('infrastructure') {
        def buildInfraStageName = "Build Infrastructure - ${environment}"
        if(tfPlanOnly){
          buildInfraStageName = "Terraform Plan -${environment}"
        }
        stageWithAgent(buildInfraStageName, product) {
          onPreview {
            deploymentNumber = githubCreateDeployment()
          }

          withSubscription(subscription) {
            dir('infrastructure') {
                sectionInfraBuild(
                  subscription: subscription,
                  environment: environment,
                  aksSubscription: aksSubscription,
                  product: product,
                  component: component,
                  pipelineCallbacksRunner: pcr,
                  planOnly: tfPlanOnly,
                  expires: config.expiryDate
                )
            }

            if(!tfPlanOnly){

              if (config.migrateDb) {
                stageWithAgent("DB Migration - ${environment}", product) {
                  pcr.callAround("dbmigrate:${environment}") {
                    builder.dbMigrate(
                      tfOutput?.vaultName ? tfOutput.vaultName.value : "${config.dbMigrationVaultName}-${environment}",
                      component
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
}
