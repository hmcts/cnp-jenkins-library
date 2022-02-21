#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  Builder builder = params.builder

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def pactBrokerUrl = params.pactBrokerUrl
  def acr
  def dockerImage
  def projectBranch
  def imageRegistry
  boolean noSkipImgBuild = true

  stageWithAgent('Checkout', product) {
    checkoutScm(pipelineCallbacksRunner: pcr)
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
  }

  onPathToLive {
    stageWithAgent("Build", product) {
      onPR {
        enforceChartVersionBumped product: product, component: component
        warnAboutAADIdentityPreviewHack product: product, component: component
      }

      builder.setupToolVersion()

      if (!fileExists('Dockerfile')) {
        error "Please add a Dockerfile"
      }

      // always build master and demo as we currently do not deploy an image there
      boolean envSub = autoDeployEnvironment() != null
      when(noSkipImgBuild || projectBranch.isMaster() || envSub) {
        pcr.callAround('build') {
          timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
            builder.build()
          }
        }
      }
    }

    stageWithAgent("Tests/Checks/Container build", product) {
      when(noSkipImgBuild) {
        parallel(

          "Unit tests and Sonar scan": {
            pcr.callAround('test') {
              timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'test') {
                builder.test()
              }
            }

            pcr.callAround('sonarscan') {
              pluginActive('sonar') {
                withSonarQubeEnv("SonarQube") {
                  builder.sonarScan()
                }

                timeoutWithMsg(time: 30, unit: 'MINUTES', action: 'Sonar Scan') {
                  def qg = waitForQualityGate()
                  if (qg.status != 'OK') {
                    error "Pipeline aborted due to quality gate failure: ${qg.status}"
                  }
                }
              }
            }
          },

          "Security Checks": {
            pcr.callAround('securitychecks') {
              builder.securityCheck()
            }
          },

          "Docker Build": {
            withAcrClient(subscription) {
              def acbTemplateFilePath = 'acb.tpl.yaml'

              pcr.callAround('dockerbuild') {
                timeoutWithMsg(time: 30, unit: 'MINUTES', action: 'Docker build') {
                  if (!fileExists('.dockerignore')) {
                    writeFile file: '.dockerignore', text: libraryResource('uk/gov/hmcts/.dockerignore_build')
                  } else {
                    writeFile file: '.dockerignore_build', text: libraryResource('uk/gov/hmcts/.dockerignore_build')
                    sh script: """
                      # in case anyone doesn't have a trailing new line in their file
                      echo -e '\n' >> .dockerignore
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
          },

          "Docker Test Build": {
            def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()
            if (isOnMaster && fileExists('build.gradle')) {
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
            } else {
              echo "Not on Master branch (or not using gradle). Skipping docker test build."
            }
          },

          failFast: true
        )
      }
    }

    if (config.pactBrokerEnabled && config.pactConsumerTestsEnabled && noSkipImgBuild) {
      stageWithAgent("Pact Consumer Verification", product) {
        timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'Pact Consumer Verification') {
          def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
          def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

          env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
          env.PACT_BROKER_URL = pactBrokerUrl

          /*
         * These instructions have to be kept in order
         */
          pcr.callAround('pact-consumer-tests') {
            builder.runConsumerTests(pactBrokerUrl, version)
          }
        }
      }
    }
  }
}
