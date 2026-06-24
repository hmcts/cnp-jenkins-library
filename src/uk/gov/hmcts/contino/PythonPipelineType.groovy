package uk.gov.hmcts.contino

class PythonPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  PythonBuilder builder

  PythonPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new PythonBuilder(steps)
  }
}
