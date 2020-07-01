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

    builder = new YarnBuilder(steps)
  }
}
