#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

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
    pcr.callAround('checkout') {
      checkoutScm()
      withAcrClient(subscription) {
        projectBranch = new ProjectBranch(env.BRANCH_NAME)
        imageRegistry = env.TEAM_CONTAINER_REGISTRY ?: env.REGISTRY_NAME
        acr = new Acr(this, subscription, imageRegistry, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
        dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT)
        noSkipImgBuild = env.NO_SKIP_IMG_BUILD?.trim()?.toLowerCase() == 'true' || !acr.hasTag(dockerImage)
      }
    }
  }


  stageWithAgent("Build", product) {
    onPR {
      enforceChartVersionBumped product: product, component: component
      warnAboutAADIdentityPreviewHack product: product, component: component
    }

    builder.setupToolVersion()

    if (!fileExists('Dockerfile')) {
      WarningCollector.addPipelineWarning("deprecated_no_dockerfile", "A Dockerfile will be required for all app builds. Docker builds (enableDockerBuild()) wil be enabled by default. ", new Date().parse("dd.MM.yyyy", "17.12.2019"))
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
            def dockerfileTest = 'Dockerfile_test'
            def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

            pcr.callAround('dockerbuild') {
              timeoutWithMsg(time: 30, unit: 'MINUTES', action: 'Docker build') {
                if (!fileExists('.dockerignore')) {
                  writeFile file: '.dockerignore', text: libraryResource('uk/gov/hmcts/.dockerignore_build')
                } else {
                  writeFile file: '.dockerignore_build', text: libraryResource('uk/gov/hmcts/.dockerignore_build')
                  sh script: "cat .dockerignore_build >> .dockerignore"
                }
                def buildArgs = projectBranch.isPR() ? " --build-arg DEV_MODE=true" : ""
                if (fileExists(acbTemplateFilePath)) {
                  acr.runWithTemplate(acbTemplateFilePath, dockerImage)
                } else {
                  acr.build(dockerImage, buildArgs)
                }
                if (isOnMaster && fileExists('build.gradle')) {
                  writeFile file: '.dockerignore', text: libraryResource('uk/gov/hmcts/gradle/.dockerignore_test')
                  writeFile file: 'runTests.sh', text: libraryResource('uk/gov/hmcts/gradle/runTests.sh')
                  if (!fileExists(dockerfileTest)) {
                    writeFile file: dockerfileTest, text: libraryResource('uk/gov/hmcts/gradle/Dockerfile_test')
                  }
                  def dockerImageTest = new DockerImage(product, "${component}-${DockerImage.TEST_REPO}", acr, projectBranch.imageTag(), env.GIT_COMMIT)
                  acr.build(dockerImageTest, " -f ${dockerfileTest}")
                }
              }
            }
          }
        },

        failFast: true
      )
    }
  }

  if (config.pactBrokerEnabled) {
    stageWithAgent("Pact Consumer Verification", product) {
      def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
      def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

      env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
      env.PACT_BROKER_URL = pactBrokerUrl

      /*
       * These instructions have to be kept in order
       */

      if (config.pactConsumerTestsEnabled) {
        pcr.callAround('pact-consumer-tests') {
          builder.runConsumerTests(pactBrokerUrl, version)
        }
      }
    }
  }

}
