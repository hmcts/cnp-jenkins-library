package uk.gov.hmcts.contino

class MockJenkins {
  MockJenkinsPluginManager pluginManager = new MockJenkinsPluginManager()

  def getPluginManager() {
    return pluginManager
  }
}
