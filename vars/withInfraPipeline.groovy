import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.pipeline.TeamConfig

def call(String product, String component = null, Closure body) {

  Subscription subscription = new Subscription(env)
  Environment environment = new Environment(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "", subscription.prodName )

  def pipelineConfig = new InfraPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbackRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new InfraPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config
  
  echo '' + callbacks.bodies.keySet()

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      stageWithAgent('Checkout', product) {
        checkoutScm()
      }

      onMaster {
        sectionInfraBuild(
          pipelineConfig: pipelineConfig,
          subscription: subscription.nonProdName,
          environment: environment.nonProdName,
          product: product,
          component: component)

        sectionInfraBuild(
          pipelineConfig: pipelineConfig,
          subscription: subscription.prodName,
          environment: environment.prodName,
          product: product,
          component: component)

        sectionSyncBranchesWithMaster(
          branchestoSync: pipelineConfig.branchesToSyncWithMaster,
          product: product
        )
      }

      onAutoDeployBranch { subscriptionName, environmentName, aksSubscription ->
        sectionInfraBuild(
          pipelineConfig: pipelineConfig,
          subscription: subscriptionName,
          environment: environmentName,
          product: product,
          component: component)
      }

      onPR {
        sectionInfraBuild(
          pipelineConfig: pipelineConfig,
          subscription: subscription.nonProdName,
          environment: environment.nonProdName,
          product: product,
          planOnly: true,
          component: component)
      }
    } catch (err) {
      currentBuild.result = "FAILURE"
      notifyBuildFailure channel: slackChannel

      callbackRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      notifyPipelineDeprecations(slackChannel, metricsPublisher)
      if (env.KEEP_DIR_FOR_DEBUGGING != "true") {
        deleteDir()
      }
    }

    notifyBuildFixed channel: slackChannel

    callbackRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
