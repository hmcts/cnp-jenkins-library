import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.MetricsPublisher

def call(type, String product, String component, Closure body) {
  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, component),
    nodejs: new NodePipelineType(this, product, component)
  ]

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  Deployer deployer = pipelineType.deployer
  Builder builder = pipelineType.builder

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  def pl = new PipelineCallbacks(metricsPublisher)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }
  currentBuild.result = "SUCCESS"

  timestamps {
    try {
      node {
        env.PATH = "$env.PATH:/usr/local/bin"

        stage('Checkout') {
          pl.callAround('checkout') {
            deleteDir()
            checkout scm
          }
        }

        stage("Build") {
          pl.callAround('build') {
            builder.build()
          }
        }

        stage("Test") {
          pl.callAround('test') {
            builder.test()
          }
        }

        stage("Security Checks") {
          pl.callAround('securitychecks') {
            builder.securityCheck()
          }
        }

        stage("Sonar Scan") {
          pl.callAround('sonarscan') {
            pluginActive('sonar') {
              withSonarQubeEnv("SonarQube") {
                builder.sonarScan()
              }

              timeout(time: 5, unit: 'MINUTES') {
                def qg = steps.waitForQualityGate()
                if (qg.status != 'OK') {
                  error "Pipeline aborted due to quality gate failure: ${qg.status}"
                }
              }
            }
          }
        }

        onMaster {

          folderExists('infrastructure') {
            withSubscription('nonprod') {
              dir('infrastructure') {
                withIlbIp('nonprod') {
                  spinInfra("${product}-${component}", 'nonprod', false, 'nonprod')
                  scmServiceRegistration('nonprod')
                }
              }
            }
          }

          stage('Deploy nonprod-staging') {
            pl.callAround('deploy:nonprod') {
              deployer.deploy('nonprod')
              deployer.healthCheck('nonprod')
            }
          }

          stage('Smoke Tests - nonprod-staging') {
            withEnv(["TEST_URL=${deployer.getServiceUrl('nonprod')}"]) {
              pl.callAround('smoketest:nonprod') {
                echo "Using TEST_URL: '$TEST_URL'"
                builder.smokeTest()
              }
            }
          }

          stage('Functional Tests - nonprod-staging') {
            withEnv(["TEST_URL=${deployer.getServiceUrl('nonprod')}"]) {
              pl.callAround('functionaltest:nonprod') {
                echo "Using TEST_URL: '$TEST_URL'"
                builder.functionalTest()
              }
            }
          }

          stage('Promote nonprod-staging -> nonprod') {
            withSubscription('nonprod') {
              sh "az webapp deployment slot swap --name \"${product}-${component}-nonprod\" --resource-group \"${product}-${component}-nonprod\" --slot staging --target-slot production"
            }
          }

          stage("OWASP") {

          }

//        folderExists('infrastructure') {
//          withSubscription('prod') {
//            dir('infrastructure') {
//              withIlbIp('prod') {
//                spinInfra("${product}-${component}", 'prod', false, 'prod')
//              }
//            }
//          }
//        }
//
//        stage('Deploy Prod') {
//          pl.callAround('deploy:prod') {
//            deployer.deploy('prod')
//            deployer.healthCheck('prod')
//          }
//        }
//
//        stage('Smoke Tests - Prod') {
//          withEnv(["TEST_URL=${deployer.getServiceUrl('prod')}"]) {
//            pl.callAround('smoketest:prod') {
//              builder.smokeTest()
//            }
//          }
//        }
        }
      }
    } catch (err) {
      currentBuild.result = "FAILURE"
      if (pl.slackChannel) {
        notifyBuildFailure channel: pl.slackChannel
      }

      pl.call('onFailure')
      node {
        metricsPublisher.publish('Pipeline Failed')
      }
      throw err
    }

    if (pl.slackChannel) {
      notifyBuildFixed channel: pl.slackChannel
    }

    pl.call('onSuccess')
    node {
      metricsPublisher.publish('Pipeline Succeeded')
    }
  }
}
