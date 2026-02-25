#!groovy
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig

  def subscription = params.subscription
  def product = params.product
  def component = params.component

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

  boolean dockerFileExists = fileExists('Dockerfile')

  onPathToLive {
    def branches = [failFast: false]

    if (dockerFileExists) {
      branches["Docker Build"] = {
        withAcrClient(subscription) {
          def acbTemplateFilePath = 'acb.tpl.yaml'

          pcr.callAround('dockerbuild') {
            timeoutWithMsg(time: 80, unit: 'MINUTES', action: 'Docker build') {
              if (!fileExists('.dockerignore')) {
                writeFile file: '.dockerignore', text: libraryResource('uk/gov/hmcts/.dockerignore_build')
              } else {
                writeFile file: '.dockerignore_build', text: libraryResource('uk/gov/hmcts/.dockerignore_build')
                sh script: """
                        # in case anyone doesn't have a trailing new line in their file
                        printf '\r\n' >> .dockerignore
                        cat .dockerignore_build >> .dockerignore
                      """
              }
              def buildArgs = projectBranch.isPR() ? " --build-arg DEV_MODE=true" : ""
              if (fileExists(acbTemplateFilePath)) {
                acr.runWithTemplate(acbTemplateFilePath, dockerImage)
              } else {
                acr.build(dockerImage, buildArgs)
              }
            }
          }
        }
      }
    }

    onMaster {
      if (config.dockerTestBuild && fileExists('build.gradle') && dockerFileExists) {
        branches["Docker Test Build"] = {
          withAcrClient(subscription) {
            def dockerfileTest = 'Dockerfile_test'

            pcr.callAround('dockertestbuild') {
              timeoutWithMsg(time: 30, unit: 'MINUTES', action: 'Docker test build') {
                writeFile file: 'Dockerfile_test.dockerignore', text: libraryResource('uk/gov/hmcts/gradle/.dockerignore_test')
                writeFile file: 'runTests.sh', text: libraryResource('uk/gov/hmcts/gradle/runTests.sh')
                if (!fileExists(dockerfileTest)) {
                  writeFile file: dockerfileTest, text: libraryResource('uk/gov/hmcts/gradle/Dockerfile_test')
                }
                def dockerImageTest = new DockerImage(product, "${component}-${DockerImage.TEST_REPO}", acr, projectBranch.imageTag(), env.GIT_COMMIT, env.LAST_COMMIT_TIMESTAMP)
                acr.build(dockerImageTest, " -f ${dockerfileTest}")
              }
            }
          }
        }
      }
    }

    stageWithAgent("Container build", product) {
      when(noSkipImgBuild && branches.size() > 1) {
        parallel branches
      }
    }

    if (noSkipImgBuild) {
      stageWithAgent("Promote Docker Image", product) {
        if (dockerFileExists) {
          def deploymentStage = DockerImage.DeploymentStage.STAGING
          def isOnPreview = new ProjectBranch(env.BRANCH_NAME).isPreview()
          if (isOnPreview) {
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
  }
}
