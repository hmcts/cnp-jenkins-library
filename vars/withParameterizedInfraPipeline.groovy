import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.TeamConfig
import uk.gov.hmcts.pipeline.LibraryBranchControls

  def libraryBranchAllowed = new LibraryBranchControls(this).isBranchAllowed(pipelineConfig)

def call(String product, String environment, String subscription, Closure body) {
  call(product, environment, subscription, false, null, body)
}

def call(String product, String environment, String subscription, String component, Closure body) {
  call(product, environment, subscription, false, component, body)
}
def call(String product, String environment, String subscription, Boolean planOnly, Closure body) {
  call(product, environment, subscription, planOnly, body)
}
def call(String product, String environment, String subscription, Boolean planOnly, String component, Closure body) {

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "")

  def pipelineConfig = new InfraPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage, stageDurationMillis ->
    metricsPublisher.publish(stage, stageDurationMillis)
  }

  def dsl = new InfraPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  def libraryBranchAllowed = new LibraryBranchControls(this).isBranchAllowed(pipelineConfig)

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      if (!libraryBranchAllowed) {
        currentBuild.result = "FAILURE"
        return
      }

      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      stageWithAgent('Checkout', product) {
        checkoutScm(pipelineCallbacksRunner: callbacksRunner)
      }


      sectionInfraBuild(
        pipelineConfig: pipelineConfig,
        subscription: subscription,
        environment: environment,
        planOnly: planOnly,
        deploymentTargets: null,
        product: product,
        component: component,
        expires: pipelineConfig.expiryDate,
        pipelineCallbacksRunner: callbacksRunner,
      )


    } catch (err) {
      currentBuild.result = "FAILURE"
      notifyBuildFailure channel: slackChannel

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      notifyPipelineDeprecations(slackChannel, metricsPublisher)
      if (env.KEEP_DIR_FOR_DEBUGGING != "true") {
        deleteDir()
      }
    }

    notifyBuildFixed channel: slackChannel

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
