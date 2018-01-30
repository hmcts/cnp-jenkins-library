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
  def sonarScan() {
    return null
  }

  @Override
  def smokeTest() {
    return null
  }

  @Override
  def functionalTest(){
    return null
  }

  @Override
  def securityCheck() {
    return null
  }

  @Override
  def addVersionInfo() {
    return null
  }
}
