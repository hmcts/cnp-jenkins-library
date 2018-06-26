package uk.gov.hmcts.contino

class CrossBrowserPipeline implements  PipelineType, Serializable {

  def steps
  def product
  def app

  NightlyBuilder builder
  Deployer deployer


  CrossBrowserPipeline(steps,product) {
      this.steps = steps
      this.product = product
      builder = new CrossBrowserPipelineSteps(steps, product)

  }
}
