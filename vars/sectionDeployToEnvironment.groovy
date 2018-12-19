#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType


def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  def deploymentTargets = params.deploymentTargets ?: deploymentTargets(subscription, environment)
  Long deploymentNumber

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer

  lock(resource: "${product}-${component}-${environment}-deploy", inversePrecedence: true) {
    stage("Build Infrastructure - ${environment}") {
      onPreview {
        deploymentNumber = githubCreateDeployment()
      }

      folderExists('infrastructure') {
        withSubscription(subscription) {
          dir('infrastructure') {
            pl.callAround("buildinfra:${environment}") {
              timeoutWithMsg(time: 120, unit: 'MINUTES', action: "buildinfra:${environment}") {
                withIlbIp(environment) {
                  def additionalInfrastructureVariables = collectAdditionalInfrastructureVariablesFor(subscription, product, environment)
                  withEnv(additionalInfrastructureVariables) {
                    tfOutput = spinInfra(product, component, environment, false, subscription)
                  }
                  scmServiceRegistration(environment)
                }
              }
            }
          }
          if (pl.migrateDb) {
            stage("DB Migration - ${environment}") {
              pl.callAround("dbmigrate:${environment}") {
                builder.dbMigrate(tfOutput.vaultName.value, tfOutput.microserviceName.value)
              }
            }
          }
        }
      }
    }

    if (pl.legacyDeployment) {
      deploymentTargets.add(0, '')
    }
    for (deploymentTarget in deploymentTargets) {
      sectionDeployToDeploymentTarget(
        pipelineCallbacks: pl,
        pipelineType: pipelineType,
        subscription: subscription,
        environment: environment,
        product: product,
        component: component,
        deploymentTarget: deploymentTarget)
    }
  }
}
