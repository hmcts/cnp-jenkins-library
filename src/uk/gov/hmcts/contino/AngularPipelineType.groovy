package uk.gov.hmcts.contino

public class AngularPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder

  AngularPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    builder = new AngularBuilder(steps)
  }
}
