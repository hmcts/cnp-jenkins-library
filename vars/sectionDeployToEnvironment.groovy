#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(params) {
  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType
  def pactBrokerUrl = params.pactBrokerUrl
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  def deploymentTargets = params.deploymentTargets ?: deploymentTargets(subscription, environment)
  Long deploymentNumber

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer
  def tfOutput
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, subscription )
  approvedEnvironmentRepository(environment, metricsPublisher) {
    lock(resource: "${product}-${component}-${environment}-deploy", inversePrecedence: true) {
      folderExists('infrastructure') {
        stage("Build Infrastructure - ${environment}") {
          onPreview {
            deploymentNumber = githubCreateDeployment()
          }

          withSubscription(subscription) {
            dir('infrastructure') {
              pcr.callAround("buildinfra:${environment}") {
                timeoutWithMsg(time: 120, unit: 'MINUTES', action: "buildinfra:${environment}") {
                  withIlbIp(subscription, environment) {
                    def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                    withEnv(additionalInfrastructureVariables) {
                      tfOutput = spinInfra(product, component, environment, false, subscription)
                    }
                    if (config.legacyDeploymentForEnv(environment)) {
                      scmServiceRegistration(subscription, environment)
                    }
                  }
                }
              }
            }

            registerDns(params)

            if (config.migrateDb) {
              stage("DB Migration - ${environment}") {
                pcr.callAround("dbmigrate:${environment}") {
                  if (tfOutput.microserviceName) {
                    WarningCollector.addPipelineWarning("deprecated_microservice_name_outputted", "Please remove microserviceName from your terraform outputs, if you are not outputting the microservice name (component) and instead outputting something else you will need to migrate the secrets first, example PR: https://github.com/hmcts/ccd-data-store-api/pull/540"
      , new Date().parse("dd.MM.yyyy", "05.09.2019"))
                  }

                  builder.dbMigrate(
                    tfOutput.vaultName ? tfOutput.vaultName.value : "${config.dbMigrationVaultName}-${environment}",
                    tfOutput.microserviceName? tfOutput.microserviceName.value : component
                  )
                }
              }
            }
          }
        }

        notFolderExists('infrastructure/deploymentTarget') {
          // if there's no deployment target infrastructure code then don't run deployment code for deployment targets
          deploymentTargets.clear()
        }

        if (config.legacyDeploymentForEnv(environment)) {
          deploymentTargets.add(0, '')
        }

        for (int i = 0; i < deploymentTargets.size(); i++) {

          sectionDeployToDeploymentTarget(
            appPipelineConfig: config,
            pipelineCallbacksRunner: pcr,
            pipelineType: pipelineType,
            subscription: subscription,
            environment: environment,
            product: product,
            component: component,
            envTfOutput: tfOutput,
            deploymentTarget: deploymentTargets[i],
            deploymentNumber: deploymentNumber,
            pactBrokerUrl: pactBrokerUrl
          )
        }
      }
    }
  }
}
