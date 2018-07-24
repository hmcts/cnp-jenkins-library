package uk.gov.hmcts.contino

class MsBuildPipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  Builder builder
  Deployer deployer

  MsBuildPipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app

    builder = new MsBuild(steps, product)
    //deployer = new JavaDeployer(steps, product, app)
  }
}
