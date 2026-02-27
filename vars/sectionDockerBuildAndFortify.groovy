#!groovy
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.GithubAPI

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def builder = params.builder

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

    onPR {
      GithubAPI gitHubAPI = new GithubAPI(this)
      def testLabels = gitHubAPI.getLabelsbyPattern(env.BRANCH_NAME, 'enable_')
      if (testLabels.contains('enable_fortify_scan')) {
        branches["Fortify scan"] = {
          ws("${env.WORKSPACE}@fortify") {
            deleteDir()
            checkout scm
            withFortifySecrets(config.fortifyVaultName ?: "${product}-${params.environment}") {
              warnError('Failure in Fortify Scan') {
                pcr.callAround('fortify-scan') {
                  builder.fortifyScan()
                }
              }

              warnError('Failure in Fortify vulnerability report') {
                fortifyVulnerabilityReport()
              }

              archiveArtifacts allowEmptyArchive: true, artifacts: 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*'
            }
          }
        }
      }
    }

    if (config.fortifyScan && branches["Fortify scan"] == null) {
      branches["Fortify scan"] = {
        ws("${env.WORKSPACE}@fortify") {
          deleteDir()
          checkout scm
          withFortifySecrets(config.fortifyVaultName ?: "${product}-${params.environment}") {
            warnError('Failure in Fortify Scan') {
              pcr.callAround('fortify-scan') {
                builder.fortifyScan()
              }
            }

            warnError('Failure in Fortify vulnerability report') {
              fortifyVulnerabilityReport()
            }

            archiveArtifacts allowEmptyArchive: true, artifacts: 'Fortify Scan/FortifyScanReport.html,Fortify Scan/FortifyVulnerabilities.*'
          }
        }
      }
    }

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

  }
}
