#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

/*
 * The image retagging in ACR is used to promote an image to AAT.
 * This will mark the image as having passed all the prior stages.
 *
 * Any image re-tagged following the pattern displayed below is
 * not going to be rebuilt unless the commit hash changes (i.e.
 * there is a new commit) or the environment variable NO_SKIP_IMG_BUILD
 * is set:
 *
 * e.g.: <my-app-image>:aat-<commit-hash>
 */

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  stage('AAT build promotion') {
    if (config.dockerBuild) {
      withAksClient(subscription) {

        def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
        def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag(), env.GIT_COMMIT)

        pcr.callAround('aatpromotion') {
          acr.retagForStage(DockerImage.DeploymentStage.AAT, dockerImage)
        }
      }
    }
  }
}
