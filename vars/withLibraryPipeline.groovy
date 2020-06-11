import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.pipeline.AKSSubscriptions
import uk.gov.hmcts.pipeline.TeamConfig

def call(type, String product, String component, Closure body) {

  def branch = new ProjectBranch(env.BRANCH_NAME)

  def deploymentNamespace = branch.deploymentNamespace()
  def deploymentProduct = deploymentNamespace ? "$deploymentNamespace-$product" : product

  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, deploymentProduct, component),
    nodejs: new NodePipelineType(this, deploymentProduct, component),
    angular: new AngularPipelineType(this, deploymentProduct, component)
  ]

  Subscription subscription = new Subscription(env)
  AKSSubscriptions aksSubscriptions = new AKSSubscriptions(this)

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, subscription.prodName )
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

  node {
    def slackChannel = new TeamConfig(this).getBuildNoticesSlackChannel(product)
    try {
      env.PATH = "$env.PATH:/usr/local/bin"

      sectionBuildAndTestNoPublish(
        appPipelineConfig: pipelineConfig,
        pipelineCallbacksRunner: callbacksRunner,
        builder: pipelineType.builder,
        subscription: subscription.nonProdName,
        environment: environment.nonProdName,
        product: product,
        component: component,
        pactBrokerUrl: environment.pactBrokerUrl
      )

      onPR {
      }

      onMaster {
      }

      onAutoDeployBranch { subscriptionName, environmentName, aksSubscription ->
      }

      onPreview {
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
