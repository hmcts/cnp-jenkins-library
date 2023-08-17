package uk.gov.hmcts.contino

class RubyPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder

  RubyPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new RubyBuilder(steps)
  }
}
