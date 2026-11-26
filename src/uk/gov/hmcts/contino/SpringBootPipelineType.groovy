package uk.gov.hmcts.contino

class SpringBootPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  GradleBuilder builder

  SpringBootPipelineType(steps, product, app) {
    this(steps, product, app, { secrets, body -> steps.withAzureKeyvault(secrets, body) })
  }

  SpringBootPipelineType(steps, product, app, Closure withAzureKeyvaultStep) {
    this.steps = steps
    this.product = product
    this.app = app

    this.steps.env.PRODUCT = product

    builder = new GradleBuilder(steps, product, withAzureKeyvaultStep)
  }
}
