#!groovy
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.azure.Acr

def call(params) {

  PipelineCallbacks pl = params.pipelineCallbacks
  Builder builder = params.builder

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  stage('Checkout') {
    pl.callAround('checkout') {
      deleteDir()
      checkout scm
    }
  }

  stage("Build") {
    pl.callAround('build') {
      timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
        builder.build()
      }
    }
  }

  stage("Tests/Checks/Container build") {

    parallel(
      "Unit tests and Sonar scan": {

        pl.callAround('test') {
          timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'test') {
            builder.test()
          }
        }

        pl.callAround('sonarscan') {
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
        pl.callAround('securitychecks') {
          builder.securityCheck()
        }
      },

      "Docker Build" : {
        if (pl.dockerBuild) {
          withAksClient(subscription) {
            def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
            def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag())

            pl.callAround('dockerbuild') {
              timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'Docker build') {
                acr.build(dockerImage)
              }
            }
          }
        }
      }
    )

  }

}
