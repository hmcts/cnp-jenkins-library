#!groovy
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  AppPipelineConfig config = params.appPipelineConfig

  def projectBranch
  def imageRegistry
  def acr
  def dockerImage
  boolean noSkipImgBuild = true

  withAcrClient(subscription) {
    projectBranch = new ProjectBranch(env.BRANCH_NAME)
    imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
    acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
    dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
    boolean hasTag = acr.hasTag(dockerImage)
    boolean envOverrideForSkip = env.NO_SKIP_IMG_BUILD?.trim()?.toLowerCase() == 'true'
    noSkipImgBuild = envOverrideForSkip || !hasTag
    echo("Checking if we should skip image build, tag: ${projectBranch.imageTag()}, git commit: ${env.GIT_COMMIT}, timestamp: ${env.LAST_COMMIT_TIMESTAMP}, hasTag: ${hasTag}, hasOverride: ${envOverrideForSkip}, result: ${!noSkipImgBuild}")
  }

  if (noSkipImgBuild) {
    stageWithAgent("Promote Docker Image", product) {
      if (fileExists('Dockerfile')) {
        def deploymentStage = DockerImage.DeploymentStage.STAGING
        if (projectBranch.isPreview()) {
          deploymentStage = DockerImage.DeploymentStage.PREVIEW
        }
        onPR {
          deploymentStage = DockerImage.DeploymentStage.PR
        }
        withAcrClient(subscription) {
          acr.retagForStage(deploymentStage, dockerImage)
          acr.purgeOldTags(deploymentStage, dockerImage)
        }
      }
    }
  }

  if (config.pactBrokerEnabled && config.pactConsumerTestsEnabled && noSkipImgBuild) {
    stageWithAgent("Pact Consumer Verification", product) {
      timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'Pact Consumer Verification') {
        def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
        def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

        env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
        env.PACT_BROKER_URL = env.PACT_BROKER_URL ?: 'https://pact-broker.platform.hmcts.net'
        env.PACT_BROKER_SCHEME = env.PACT_BROKER_SCHEME ?: 'https'
        env.PACT_BROKER_PORT = env.PACT_BROKER_PORT ?: '443'

        /*
        * These instructions have to be kept in order
        */
        pcr.callAround('pact-consumer-tests') {
          builder.runConsumerTests(env.PACT_BROKER_URL, version)
        }
      }
    }
  }
}
