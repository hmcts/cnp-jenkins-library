#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

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

  stage("${deploymentStage.label} build promotion") {
    if (config.dockerBuild) {
      withAcrClient(subscription) {

        def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
        def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag(), env.GIT_COMMIT)

        pcr.callAround("${deploymentStage.label}:promotion") {
          acr.retagForStage(deploymentStage, dockerImage)
          if (DockerImage.DeploymentStage.PROD == deploymentStage) {
            acr.retagForStage(DockerImage.DeploymentStage.LATEST, dockerImage)
          }
        }
      }
    }
  }
}
