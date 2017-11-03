package uk.gov.hmcts.contino

interface Builder {
  def build()
  def test()
  def sonarScan()
  def smokeTest()
  def securityCheck()
}
