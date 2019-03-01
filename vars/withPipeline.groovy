import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.Subscription

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
  def pl = new PipelineCallbacks(metricsPublisher, this)

  body.delegate = pl
  body.call() // register callbacks

  pl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  Environment environment = new Environment(env)

  timestamps {
    node {
      try {
        env.PATH = "$env.PATH:/usr/local/bin"

        sectionBuildAndTest(
          pipelineCallbacks: pl,
          builder: pipelineType.builder,
          subscription: subscription.nonProdName,
          product: product,
          component: component
        )

        sectionCI(
          pipelineCallbacks: pl,
          pipelineType: pipelineType,
          subscription: subscription.nonProdName,
          environment: environment.nonProdName,
          product: product,
          component: component
        )

        onMaster {
          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscription.nonProdName,
            environment: environment.nonProdName,
            product: product,
            component: component)

          if (pl.installCharts) {
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
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscription.prodName,
            environment: environment.prodName,
            product: product,
            component: component)
        }

        onAutoDeployBranch { subscriptionName, environmentName ->
          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscriptionName,
            environment: environmentName,
            product: product,
            component: component)
        }

        onPreview {
          sectionDeployToEnvironment(
            pipelineCallbacks: pl,
            pipelineType: pipelineType,
            subscription: subscription.previewName,
            environment: environment.previewName,
            product: deploymentProduct,
            component: component)
        }
      } catch (err) {
        currentBuild.result = "FAILURE"
        if (pl.slackChannel) {
          notifyBuildFailure channel: pl.slackChannel
        }

        pl.call('onFailure')
        metricsPublisher.publish('Pipeline Failed')
        throw err
      }

      if (pl.slackChannel) {
        notifyBuildFixed channel: pl.slackChannel
      }

      pl.call('onSuccess')
      metricsPublisher.publish('Pipeline Succeeded')
    }
  }
}
