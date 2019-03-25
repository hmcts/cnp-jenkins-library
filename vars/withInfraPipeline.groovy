import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.Environment

def call(String product, Closure body) {

  Subscription subscription = new Subscription(env)
  Environment environment = new Environment(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "", subscription )

  def pipelineConfig = new InfraPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbackRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new InfraPipelineDsl(callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  timestamps {
    node {
      try {
        env.PATH = "$env.PATH:/usr/local/bin"

        stage('Checkout') {
          deleteDir()
          checkout scm
        }

        onMaster {
          sectionInfraBuild(
            pipelineConfig: pipelineConfig,
            subscription: subscription.nonProdName,
            environment: environment.nonProdName,
            product: product)

          sectionInfraBuild(
            pipelineConfig: pipelineConfig,
            subscription: subscription.prodName,
            environment: environment.prodName,
            product: product)
        }

        onAutoDeployBranch { subscriptionName, environmentName ->
          sectionInfraBuild(
            pipelineConfig: pipelineConfig,
            subscription: subscriptionName,
            environment: environmentName,
            product: product)
        }

        onPR {
          sectionInfraBuild(
            pipelineConfig: pipelineConfig,
            subscription: subscription.nonProdName,
            environment: environment.nonProdName,
            product: product,
            planOnly: true)
        }
      } catch (err) {
        currentBuild.result = "FAILURE"
        if (pipelineConfig.slackChannel) {
          notifyBuildFailure channel: pipelineConfig.slackChannel
        }

        callbackRunner.call('onFailure')
        metricsPublisher.publish('Pipeline Failed')
        throw err
      }

      if (pipelineConfig.slackChannel) {
        notifyBuildFixed channel: pipelineConfig.slackChannel
      }

      callbackRunner.call('onSuccess')
      metricsPublisher.publish('Pipeline Succeeded')
    }
  }
}
