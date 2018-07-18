package uk.gov.hmcts.contino

interface Builder {
  def build()
  def test()
  def sonarScan()
  def smokeTest()
  def functionalTest()
  def performanceTest()
  def crossBrowserTest()
  def securityCheck()
  def addVersionInfo()
}
