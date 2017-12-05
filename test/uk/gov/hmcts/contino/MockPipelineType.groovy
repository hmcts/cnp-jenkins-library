package uk.gov.hmcts.contino

class MockPipelineType implements PipelineType, Serializable {
  Builder builder = new MockBuilder()
  Deployer deployer = new MockDeployer()

}
