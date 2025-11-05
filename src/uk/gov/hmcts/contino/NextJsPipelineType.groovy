package uk.gov.hmcts.contino

class NextJsPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  NextJsBuilder builder

  NextJsPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new NextJsBuilder(steps)
  }
}
