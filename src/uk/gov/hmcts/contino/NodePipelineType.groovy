package uk.gov.hmcts.contino;

public class NodePipelineType implements PipelineType, Serializable {
  def steps
  def product
  def app

  NodePipelineType(steps, product, app) {
    this.steps = steps
    this.product = product
    this.app = app
  }

  Builder builder = new YarnBuilder(steps)

  Deployer deployer = new NodeDeployer(steps, product, app)
}
