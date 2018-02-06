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

          def subscription = 'nonprod'
          if (env.NONPROD_SUBSCRIPTION) {
            subscription = env.NONPROD_SUBSCRIPTION
          }
          def environment = 'aat'
          if (env.NONPROD_ENVIRONMENT) {
            environment = env.NONPROD_ENVIRONMENT
          }

          stage("Build Infrastructure - ${environment}") {
            folderExists('infrastructure') {
              withSubscription(subscription) {
                dir('infrastructure') {
                  withIlbIp(environment) {
                    spinInfra("${product}-${component}", environment, false, subscription)
                    scmServiceRegistration(environment)
                  }
                }
              }
            }
          }

          stage("Deploy - ${environment} (staging slot)") {
            pl.callAround("deploy:${environment}") {
              deployer.deploy(environment)
              deployer.healthCheck(environment)
            }
          }

          stage("Smoke Test - ${environment} (staging slot)") {
            withEnv(["TEST_URL=${deployer.getServiceUrl(environment)}"]) {
              pl.callAround('smoketest:${environment}') {
                echo "Using TEST_URL: '$TEST_URL'"
                builder.smokeTest()
              }
            }
          }

          stage("Functional Test - ${environment} (staging slot)") {
            withEnv(["TEST_URL=${deployer.getServiceUrl(environment)}"]) {
              pl.callAround('functionaltest:${environment}') {
                echo "Using TEST_URL: '$TEST_URL'"
                builder.functionalTest()
              }
            }
          }

          stage("Promote - ${environment} (staging -> production slot)") {
            withSubscription(subscription) {
              sh "az webapp deployment slot swap --name \"${product}-${component}-${environment}\" --resource-group \"${product}-${component}-${environment}\" --slot staging --target-slot production"
            }
          }

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
