import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.MetricsPublisher

def call(type, String product, String component, String environment, String subscription, Closure body) {
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

        folderExists('infrastructure') {
          withSubscription(subscription) {
            dir('infrastructure') {
              withIlbIp(environment) {
                tfOutput = spinInfra("${product}-${component}", environment, false, subscription)
                scmServiceRegistration(environment)
              }
            }
          }
          if (pl.migrateDb) {
            stage("DB Migration - ${environment}") {
              builder.dbMigrate(tfOutput.vaultName.value, tfOutput.microserviceName.value)
            }
          }
        }

        stage("Deploy $environment") {
          pl.callAround("deploy:$environment") {
            deployer.deploy(environment)
            deployer.healthCheck(environment, "staging")
          }
        }

        stage("Promote - ${environment} (staging -> production slot)") {
          withSubscription(subscription) {
            sh "az webapp deployment slot swap --name \"${product}-${component}-${environment}\" --resource-group \"${product}-${component}-${environment}\" --slot staging --target-slot production"
            deployer.healthCheck(environment, "production")
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
