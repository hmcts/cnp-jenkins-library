package uk.gov.hmcts.contino

class MockBuilder implements Builder, Serializable {
  @Override
  def build() {
    return null
  }

  @Override
  def test() {
    return null
  }

  @Override
  def smokeTest() {
    return null
  }

  @Override
  def securityCheck() {
    return null
  }
}
