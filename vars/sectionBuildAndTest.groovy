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
        withAksClient(subscription) {
          projectBranch = new ProjectBranch(env.BRANCH_NAME)
          acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
          dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT)
          noSkipImgBuild = !acr.hasTag(dockerImage) && !env.NO_SKIP_IMG_BUILD?.trim()
        }
      }
    }
  }


  stage("Build") {
    // always build as some projects depend on this to run their smoke/functional tests
    pcr.callAround('build') {
      timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
        builder.build()
      }
    }
  }

  stage("Tests/Checks/Container Build") {

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
            withAksClient(subscription) {

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

}
