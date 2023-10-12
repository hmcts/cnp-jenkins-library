package uk.gov.hmcts.contino

class SpringBootPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app
  def isFrontend

  Builder builder

  SpringBootPipelineType(steps, product, app, isFrontend = null) {
    this.steps = steps
    this.product = product
    this.app = app
    this.isFrontend = isFrontend

    this.steps.env.PRODUCT = product
    this.steps.env.IS_FRONTEND = isFrontend

    builder = new GradleBuilder(steps, product)
  }
}
