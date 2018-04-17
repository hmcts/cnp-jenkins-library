package uk.gov.hmcts.contino

import static org.mockito.Mockito.*

class MockPipelineType implements PipelineType, Serializable {

  private static final PipelineType INSTANCE = new MockPipelineType()

  private static MockPipelineType getInstance() {
    return INSTANCE
  }

  Builder builder = mock(Builder)
  Deployer deployer = mock(Deployer)

}
