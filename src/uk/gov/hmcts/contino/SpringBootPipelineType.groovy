package uk.gov.hmcts.contino

class SpringBootPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  SpringBootPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app
  }

  Builder builder = new GradleBuilder(steps)

  Deployer deployer = new JavaDeployer(steps, product, app)
}
