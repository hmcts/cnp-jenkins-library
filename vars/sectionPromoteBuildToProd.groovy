#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

/*
 * The image retagging in ACR is used to promote an image to Prod.
 * This will mark the image as having passed all the prior stages.
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

  stage('Prod build promotion') {
    if (config.dockerBuild) {
      withAksClient(subscription) {

        def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
        def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag(), env.GIT_COMMIT)

        pcr.callAround('prodpromotion') {
          acr.retagForStage(DockerImage.DeploymentStage.PROD, dockerImage)
        }
      }
    }
  }
}
