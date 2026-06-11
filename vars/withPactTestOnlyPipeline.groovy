import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.PythonPipelineType
import uk.gov.hmcts.contino.RubyPipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.pipeline.TeamConfig

def call(type, String product, String component, Closure body) {

  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, component),
    nodejs: new NodePipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component),
    ruby: new RubyPipelineType(this, product, component),
    python: new PythonPipelineType(this, product, component)
  ]

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  dsl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  Environment environment = new Environment(env)

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      onMaster {

        PipelineCallbacksRunner pcr = callbacksRunner
        AppPipelineConfig config = pipelineConfig
        Builder builder = pipelineType.builder

        boolean noSkipImgBuild = true

        stageWithAgent('Checkout', product) {
          checkoutScm(pipelineCallbacksRunner: callbacksRunner)
        }

        stageWithAgent("Build", product) {

          builder.setupToolVersion()
        }

        stageWithAgent("Tests", product) {

          when(noSkipImgBuild) {
                pcr.callAround('test') {
                  timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'test') {
                    builder.test()
                  }
                }
          }
        }

        if(config.pactConsumerTestsEnabled) {

        stageWithAgent("Pact Consumer Verification", product) {

          def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
          def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

          env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
          env.PACT_BROKER_URL = env.PACT_BROKER_URL ?: 'https://pact-broker.platform.hmcts.net'
          env.PACT_BROKER_SCHEME = env.PACT_BROKER_SCHEME ?: 'https'
          env.PACT_BROKER_PORT = env.PACT_BROKER_PORT ?: '443'

          /*
         * These instructions have to be kept in order
         */
          pcr.callAround('pact-consumer-tests') {
            builder.runConsumerTests(env.PACT_BROKER_URL, version)
          }
        }
      }
      }
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
