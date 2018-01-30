package uk.gov.hmcts.contino

interface Builder {
  def build()
  def test()
  def sonarScan()
  def smokeTest()
  def functionalTest()
  def securityCheck()
  def addVersionInfo()
}
