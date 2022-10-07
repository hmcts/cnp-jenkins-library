package uk.gov.hmcts.contino;

public class PythonPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder

  PythonPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    builder = new YarnBuilder(steps)
  }
}
