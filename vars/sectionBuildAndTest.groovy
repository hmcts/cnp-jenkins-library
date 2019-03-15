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

  stage('Checkout') {
    pcr.callAround('checkout') {
      deleteDir()
      def scmOut = checkout scm
      echo "SCM_OUT = [${scmOut}]"
    }
  }

  stage("Build") {
    pcr.callAround('build') {
      timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
        builder.build()
      }
    }
  }

  stage("Tests/Checks/Container build") {

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

      "Docker Build" : {
        if (config.dockerBuild) {
          withAksClient(subscription) {

            def acbTemplateFilePath = 'acb.tpl.yaml'
            def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
            def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag())

            pcr.callAround('dockerbuild') {
              timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Docker build') {
                fileExists(acbTemplateFilePath) ?
                  acr.runWithTemplate(acbTemplateFilePath, dockerImage)
                  : acr.build(dockerImage)
              }
            }
          }
        }
      }
    )

  }

}
