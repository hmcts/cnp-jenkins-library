package uk.gov.hmcts.contino

class SpringBootPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder
  Deployer deployer

  SpringBootPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    builder = new GradleBuilder(steps, product)
    deployer = new JavaDeployer(steps, product, app)
  }
}
