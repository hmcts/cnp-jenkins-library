package uk.gov.hmcts.contino

class DotNetPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder
  Deployer deployer

  DotNetPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    builder = new DotNetBuilder(steps, product)
    //deployer = new JavaDeployer(steps, product, app)
  }
}
