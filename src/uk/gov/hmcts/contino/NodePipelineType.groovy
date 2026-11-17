package uk.gov.hmcts.contino;

public class NodePipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  YarnBuilder builder

  NodePipelineType(steps, product, app) {
    this(steps, product, app, { secrets, body -> steps.withAzureKeyvault(secrets, body) })
  }

  NodePipelineType(steps, product, app, Closure withAzureKeyvaultStep) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new YarnBuilder(steps, withAzureKeyvaultStep)
  }
}
