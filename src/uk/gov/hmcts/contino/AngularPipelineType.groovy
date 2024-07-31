package uk.gov.hmcts.contino

class AngularPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  AngularBuilder builder

  AngularPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new AngularBuilder(steps)
  }
}
