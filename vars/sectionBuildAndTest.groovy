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
  def builder = params.builder

  def subscription = params.subscription
  def product = params.product
  def component = params.component
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
    builder.setupToolVersion()
  }
  warnAboutJitpackRemoval(product: product, component: component)
  
  stage('ACR Migration Check') {
    warnAboutOldAcrReferences(env.GIT_URL ?: 'unknown')
  }
  
  onPathToLive {
    stageWithAgent("Build", product) {
      onPR {
        enforceChartVersionBumped product: product, component: component
        warnAboutAADIdentityPreviewHack product: product, component: component
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

    def branches = [failFast: false]
    branches["Unit tests and Sonar scan"] = {
      pcr.callAround('test') {
        timeoutWithMsg(time: 40, unit: 'MINUTES', action: 'test') {
          withAcrClient(subscription){
            acr.login()
            builder.test()
          }
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
    }
    branches["Security Checks"] = {
      pcr.callAround('securitychecks') {
        builder.securityCheck()
      }
    }
    branches["Tech Stack"] = {
      pcr.callAround('techstack') {
        builder.techStackMaintenance()
      }
    }

    stageWithAgent("Static checks", product) {
      when(noSkipImgBuild) {
        parallel branches

        // files related to dependency checking that are not needed for the rest of the pipeline
        // they can't be safely deleted in the parallel branches as docker build context will collect files
        // and then upload them, if any are missing it will error:
        // ERROR: [Errno 2] No such file or directory: './sorted-yarn-audit-issues'
        sh "rm -f new_vulnerabilities unneeded_suppressions sorted-yarn-audit-issues sorted-yarn-audit-known-issues active_suppressions unused_suppressions depsProc languageProc || true"
      }
    }
  }
}
