package uk.gov.hmcts.contino

class MockJenkins {

  private final MockJenkinsPluginManager pluginManager

  MockJenkins() {
    this. pluginManager = new MockJenkinsPluginManager()
  }

  MockJenkins(MockJenkinsPluginManager pluginManager) {
    this.pluginManager = pluginManager
  }

  def getPluginManager() {
    return this.pluginManager
  }
}
