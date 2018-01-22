package uk.gov.hmcts.contino

interface Builder {
  def build()
  def test()
  def sonarScan()
  def smokeTest()
  def e2eTest()
  def securityCheck()
  def addVersionInfo()
}
