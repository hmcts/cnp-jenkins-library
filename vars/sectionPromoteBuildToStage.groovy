#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
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

  if(fileExists('Dockerfile')) {
    PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
    AppPipelineConfig config = params.appPipelineConfig

    def subscription = params.subscription
    def environment = params.environment
    def product = params.product
    def component = params.component
    DockerImage.DeploymentStage deploymentStage = params.stage

    stageWithEnvironmentAgent("${deploymentStage.label} build promotion", product, environment) {
      if (config.immutableImagePromotion) {
        config.approvedImageReference = null
      }
      pcr.callAround("${deploymentStage.label}:promotion") {
        Map candidate
        if (config.immutableImagePromotion) {
          candidate = new LinkedHashMap(config.imageCandidate)
          if (!candidate.imageReference || candidate.sourceCommit != env.GIT_COMMIT ||
              candidate.producerJob != env.JOB_NAME || candidate.producerBuild != env.BUILD_NUMBER) {
            error 'No immutable image belongs to this source and Jenkins build'
          }
          if (deploymentStage in [DockerImage.DeploymentStage.PROD, DockerImage.DeploymentStage.LATEST] &&
              config.approvedImageReference != candidate.imageReference) {
            error 'Release evidence has not approved this immutable image'
          }
        }
        withAcrClient(subscription) {
          def imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
          def projectBranch = new ProjectBranch(env.BRANCH_NAME)
          def acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
          def dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)

          if (config.immutableImagePromotion) {
            acr.retagForStage(deploymentStage, dockerImage, candidate)
          } else {
            acr.retagForStage(deploymentStage, dockerImage)
          }
          acr.purgeOldTags(deploymentStage, dockerImage)
          if (DockerImage.DeploymentStage.PROD == deploymentStage) {
            if (config.immutableImagePromotion) {
              acr.retagForStage(DockerImage.DeploymentStage.LATEST, dockerImage, candidate)
            } else {
              acr.retagForStage(DockerImage.DeploymentStage.LATEST, dockerImage)
            }
            if (config.dockerTestBuild && projectBranch.isMaster() && fileExists('build.gradle')) {
              def dockerImageTest = new DockerImage(product, "${component}-${DockerImage.TEST_REPO}", acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
              acr.retagForStage(deploymentStage, dockerImageTest)
              acr.purgeOldTags(deploymentStage, dockerImageTest)
            }
          }
        }
        if (subscription != 'sandbox' && subscription != 'sbox') {
          reconcileFluxImageRepository product: product, component: component, environment: 'ptl'
        }
      }
    }
  }
}
