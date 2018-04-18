package uk.gov.hmcts.contino

class MockJenkinsPluginManager
{
  private final MockJenkinsPlugin[] plugins

  MockJenkinsPluginManager() {
    this.plugins = [ new MockJenkinsPlugin() ]
  }

  MockJenkinsPluginManager(MockJenkinsPlugin[] plugins) {
    this.plugins = plugins
  }

  def getPlugins() {
    return this.plugins
  }
}
