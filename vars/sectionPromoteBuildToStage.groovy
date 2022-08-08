#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.Environment

/*
 * Retagging in ACR is used to promote an image to a
 * deployment stage. These are currently AAT and Prod.
 * Promotion marks the image as having passed all the prior stages.
 *
 * For AAT any image re-tagged following the pattern displayed below is
 * not going to be rebuilt unless the commit hash changes (i.e.
 * there is a new commit) or the environment variable NO_SKIP_IMG_BUILD
 * is set:
 *
 * e.g.: <my-app-image>:aat-<commit-hash>
 *
 * The prod tag marks the image as having passed all the verification
 * and build stages and should be assigned only at the end of the pipeline.
 *
 * e.g.: <my-app-image>:prod-<commit-hash>
 */

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  DockerImage.DeploymentStage deploymentStage = params.stage

  stageWithAgent("${deploymentStage.label} build promotion", product) {
    withAcrClient(subscription) {
        def imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
        def projectBranch = new ProjectBranch(env.BRANCH_NAME)
        def acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
        def dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)

        pcr.callAround("${deploymentStage.label}:promotion") {
          acr.retagForStage(deploymentStage, dockerImage)
          def resourceGroup = env.PTL_AKS_RESOURCE_GROUP
          def clusterName = env.PTL_AKS_CLUSTER_NAME
          def aksSubscription = env.AKS_PTL_SUBSCRIPTION_NAME
          reconcileFluxImageRepository product: product, component: component, resourceGroup: resourceGroup, clusterName: clusterName, aksSubscription: aksSubscription
          if (DockerImage.DeploymentStage.PROD == deploymentStage) {
            acr.retagForStage(DockerImage.DeploymentStage.LATEST, dockerImage)
            if (projectBranch.isMaster() && fileExists('build.gradle')) {
              def dockerImageTest = new DockerImage(product, "${component}-${DockerImage.TEST_REPO}", acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
              acr.retagForStage(deploymentStage, dockerImageTest)
            }
          }
        }
      }
  }
}
