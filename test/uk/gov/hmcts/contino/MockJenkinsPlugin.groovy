package uk.gov.hmcts.contino

class MockJenkinsPlugin
{
  private final String shortName
  private final boolean isEnabled

  MockJenkinsPlugin() {
    this.shortName = "mockPlugin"
    this.isEnabled = true
  }

  MockJenkinsPlugin(String shortName, boolean isEnabled) {
    this.shortName = shortName
    this.isEnabled = isEnabled
  }
  def getShortName() {
    return this.shortName
  }
  def isEnabled() {
    return this.isEnabled
  }
}
