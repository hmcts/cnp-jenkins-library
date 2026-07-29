#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.pipeline.AcrOwnershipGate
import uk.gov.hmcts.pipeline.AcrOwnershipPolicyConfig

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
    def product = params.product
    def component = params.component
    DockerImage.DeploymentStage deploymentStage = params.stage
    def ownershipGate = new AcrOwnershipGate()
    def ownershipPolicyConfig = new AcrOwnershipPolicyConfig(
      this,
      config.approvedJenkinsConfigRepos,
      config.warnOnUnapprovedJenkinsConfigRepo
    )
    def ownershipPolicy = ownershipPolicyConfig.getOwnershipPolicy(product)

    if (ownershipPolicy.warningMessage) {
      echo(ownershipPolicy.warningMessage)
    }

    def evaluateAcrOwnership = { String repositoryName ->
      def decision = ownershipGate.evaluate(
        ownershipPolicy.mode,
        product,
        component,
        repositoryName,
        ownershipPolicy.allowList
      )

      echo(ownershipGate.logLine(decision))

      if (ownershipGate.shouldBlock(decision)) {
        error("ACR ownership gate denied write operation for repository '${repositoryName}' (reasonCode=${decision.reasonCode})")
      }
    }

    stageWithAgent("${deploymentStage.label} build promotion", product) {
      withAcrClient(subscription) {
        def imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
        def projectBranch = new ProjectBranch(env.BRANCH_NAME)
        def acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
        def dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)

        pcr.callAround("${deploymentStage.label}:promotion") {
          evaluateAcrOwnership(dockerImage.getRepositoryName())
          acr.retagForStage(deploymentStage, dockerImage)
          acr.purgeOldTags(deploymentStage, dockerImage)
          if (subscription != 'sandbox' && subscription != 'sbox') {
            reconcileFluxImageRepository product: product, component: component
          }
          if (DockerImage.DeploymentStage.PROD == deploymentStage) {
            evaluateAcrOwnership(dockerImage.getRepositoryName())
            acr.retagForStage(DockerImage.DeploymentStage.LATEST, dockerImage)
            if (config.dockerTestBuild && projectBranch.isMaster() && fileExists('build.gradle')) {
              def dockerImageTest = new DockerImage(product, "${component}-${DockerImage.TEST_REPO}", acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
              evaluateAcrOwnership(dockerImageTest.getRepositoryName())
              acr.retagForStage(deploymentStage, dockerImageTest)
              acr.purgeOldTags(deploymentStage, dockerImageTest)
            }
          }
        }
      }
    }
  }
}
