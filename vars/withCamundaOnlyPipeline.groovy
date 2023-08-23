import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.pipeline.TeamConfig
import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.RubyPipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType

def call(type, String product, String component, String s2sServiceName, String tenantId, Closure body) {
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)

  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, component),
    nodejs: new NodePipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component),
    ruby: new RubyPipelineType(this, product, component)
  ]

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  dsl.onStageFailure {
    currentBuild.result = 'FAILURE'
  }

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      PipelineCallbacksRunner pcr = callbacksRunner
      Builder builder = pipelineType.builder

      stageWithAgent('Checkout', product) {
        checkoutScm(pipelineCallbacksRunner: callbacksRunner)
        builder.setupToolVersion()
      }
      warnAboutRenovateConfig()
      parallel(
        'Unit tests and Sonar scan': {
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

              timeoutWithMsg(time: 30, unit: 'MINUTES', action: 'Sonar Scan') {
                def qg = waitForQualityGate()
                if (qg.status != 'OK') {
                  error "Pipeline aborted due to quality gate failure: ${qg.status}"
                }
              }
            }
          }
        },

        'Security Checks': {
          pcr.callAround('securitychecks') {
            builder.securityCheck()
          }
        },

        failFast: true
      )

      // Demo/ITHC/Perftest camunda upload
      onAutoDeployBranch { subscriptionName, environmentName, aksSubscription ->
        camundaPublish(s2sServiceName, environmentName, product, tenantId)
      }

      // AAT and Prod camunda promotion
      onMaster {
        Environment environment = new Environment(env)
        def nonProdEnv = environment.nonProdName
        def prodEnv = environment.prodName

        camundaPublish(s2sServiceName, nonProdEnv, product, tenantId)

        approvedEnvironmentRepository(prodEnv, metricsPublisher) {
          camundaPublish(s2sServiceName, prodEnv, product, tenantId)
        }

        sectionSyncBranchesWithMaster(
            branchestoSync: pipelineConfig.branchesToSyncWithMaster,
            product: product
        )
      }

    } catch (err) {
      currentBuild.result = 'FAILURE'
      notifyBuildFailure channel: slackChannel

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      notifyPipelineDeprecations(slackChannel, metricsPublisher)
      if (env.KEEP_DIR_FOR_DEBUGGING != 'true') {
        deleteDir()
      }
    }

    notifyBuildFixed channel: slackChannel

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
