#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.Kubectl

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
  def clusterName = params.clusterName
  def namespace = params.namespace
  def resourceGroup = params.resourceGroup
  def aksSubscription = params.aksSubscription
  def product = params.product
  def component = params.component
  def stageName = "Reconcile ACR Image"
  DockerImage.DeploymentStage deploymentStage = params.stage

  stageWithAgent(stageName, product) {  
    sh "env AZURE_CONFIG_DIR=/opt/jenkins/.azure az aks get-credentials --resource-group ${resourceGroup} --name ${clusterName} --subscription  ${aksSubscription} -a"
    sh "flux get kustomization"
  }
}
