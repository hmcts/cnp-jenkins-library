#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.PactBroker
import uk.gov.hmcts.contino.azure.Acr

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  Builder builder = params.builder

  def subscription = params.subscription
  def product = params.product
  def component = params.component
  def acr
  def dockerImage
  def projectBranch
  boolean noSkipImgBuild = true

  stage('Checkout') {
    pcr.callAround('checkout') {
      deleteDir()
      def scmVars = checkout scm
      if (scmVars) {
        env.GIT_COMMIT = scmVars.GIT_COMMIT
      }
      if (config.dockerBuild) {
        withAksClient(subscription, params.environment) {
          projectBranch = new ProjectBranch(env.BRANCH_NAME)
          acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
          dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT)
          noSkipImgBuild = env.NO_SKIP_IMG_BUILD?.trim()?.toLowerCase() == 'true' || !acr.hasTag(dockerImage)
        }
      }
    }
  }


  stage("Build") {
    builder.setupToolVersion()

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

  stage("Tests/Checks/Container build") {

    when (noSkipImgBuild) {
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

              timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Sonar Scan') {
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
          if (config.dockerBuild) {
            withAksClient(subscription, params.environment) {

              def acbTemplateFilePath = 'acb.tpl.yaml'

              pcr.callAround('dockerbuild') {
                timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Docker build') {
                  fileExists(acbTemplateFilePath) ?
                    acr.runWithTemplate(acbTemplateFilePath, dockerImage)
                    : acr.build(dockerImage)
                }
              }
            }
          }
        },

        failFast: true
      )
    }
  }

  stage("Pact verification") {
    pcr.callAround('pact-verification') {
      def version = sh(returnStdout: true, script: 'git rev-parse --verify --short HEAD')
      def pactBroker = new PactBroker(this, product, component)
      if (env.CHANGE_BRANCH || env.BRANCH_NAME == 'master') {
        // sh 'yarn test-pact'
        // sh 'yarn publish-pact'
        pactBroker.canIDeploy(version)
      }
    }
  }

}
