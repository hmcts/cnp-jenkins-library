#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.GithubAPI

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

    if (config.pactBrokerEnabled && config.pactConsumerTestsEnabled && noSkipImgBuild) {
      stageWithAgent("Pact Consumer Verification", product) {
        timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'Pact Consumer Verification') {
          def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
          def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

          env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
          env.PACT_BROKER_URL = env.PACT_BROKER_URL ?: 'https://pact-broker.platform.hmcts.net'
          env.PACT_BROKER_SCHEME = env.PACT_BROKER_SCHEME ?: 'https'
          env.PACT_BROKER_PORT = env.PACT_BROKER_PORT ?: '443'

          /*
         * These instructions have to be kept in order
         */
          pcr.callAround('pact-consumer-tests') {
            builder.runConsumerTests(env.PACT_BROKER_URL, version)
          }
        }
      }
    }
  }
}
