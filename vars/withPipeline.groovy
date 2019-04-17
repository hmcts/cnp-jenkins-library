import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.DockerImage
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

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, subscription )
  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  dsl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  Environment environment = new Environment(env)

  timestamps {
    node {
      try {
        env.PATH = "$env.PATH:/usr/local/bin"

        sectionBuildAndTest(
          appPipelineConfig: pipelineConfig,
          pipelineCallbacksRunner: callbacksRunner,
          builder: pipelineType.builder,
          subscription: subscription.nonProdName,
          environment: environment.nonProdName,
          product: product,
          component: component
        )

        sectionCI(
          appPipelineConfig: pipelineConfig,
          pipelineCallbacksRunner: callbacksRunner,
          pipelineType: pipelineType,
          subscription: subscription.nonProdName,
          environment: environment.previewName,
          product: product,
          component: component
        )

        onMaster {

          sectionPromoteBuildToStage(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            pipelineType: pipelineType,
            subscription: subscription.nonProdName,
            product: product,
            component: component,
            stage: DockerImage.DeploymentStage.AAT,
            environment: environment.nonProdName
          )

          sectionDeployToEnvironment(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            pipelineType: pipelineType,
            subscription: subscription.nonProdName,
            environment: environment.nonProdName,
            product: product,
            component: component)

          if (pipelineConfig.installCharts) {
            stage('Publish Helm chart') {
              helmPublish(
                subscriptionName: subscription.nonProdName,
                environmentName: environment.nonProdName,
                product: product,
                component: component
              )
            }
          }

          sectionDeployToEnvironment(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            pipelineType: pipelineType,
            subscription: subscription.prodName,
            environment: environment.prodName,
            product: product,
            component: component)

          sectionPromoteBuildToStage(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            pipelineType: pipelineType,
            subscription: subscription.nonProdName,
            product: product,
            component: component,
            stage: DockerImage.DeploymentStage.PROD,
            environment: environment.nonProdName
          )
        }

        onAutoDeployBranch { subscriptionName, environmentName ->
          sectionDeployToEnvironment(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            pipelineType: pipelineType,
            subscription: subscriptionName,
            environment: environmentName,
            product: product,
            component: component)
        }

        onPreview {
          sectionDeployToEnvironment(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            pipelineType: pipelineType,
            subscription: subscription.previewName,
            environment: environment.previewName,
            product: deploymentProduct,
            component: component)
        }
      } catch (err) {
        currentBuild.result = "FAILURE"
        if (pipelineConfig.slackChannel) {
          notifyBuildFailure channel: pipelineConfig.slackChannel
        }

        callbacksRunner.call('onFailure')
        metricsPublisher.publish('Pipeline Failed')
        throw err
      }

      if (pipelineConfig.slackChannel) {
        notifyBuildFixed channel: pipelineConfig.slackChannel
      }

      callbacksRunner.call('onSuccess')
      metricsPublisher.publish('Pipeline Succeeded')
    }
  }
}
