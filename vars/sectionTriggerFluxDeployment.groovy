#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

/*
 * The image retagging in ACR is used as a trigger for Weaveworks Flux
 * to (re-)deploy the image
 *
 * Any image re-tagged following the pattern displayed below is
 * eventually deployed in Flux AKS cluster:
 *
 * e.g.: <my-app-image>:aat-rc-<commit-hash>
 */

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  PipelineType pipelineType = params.pipelineType

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  stage('AAT Flux deployment') {
    if (config.dockerBuild) {
      withAksClient(subscription) {

        def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
        def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag(), env.GIT_COMMIT)

        pcr.callAround('fluxdeployment') {
          acr.retagForStage(DockerImage.DeploymentStage.AAT, dockerImage)
        }
      }
    }
  }
}
