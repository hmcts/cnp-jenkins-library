package uk.gov.hmcts.contino;

public class NodePipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder

  NodePipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new YarnBuilder(steps)
  }
}
