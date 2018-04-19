package uk.gov.hmcts.contino

class MockJenkinsPlugin
{
  private final String shortName
  private final boolean isActive

  MockJenkinsPlugin() {
    this.shortName = "mockPlugin"
    this.isActive = true
  }

  MockJenkinsPlugin(String shortName, boolean isActive) {
    this.shortName = shortName
    this.isActive = isActive
  }
  def getShortName() {
    return this.shortName
  }
  def isActive() {
    return this.isActive
  }
}
