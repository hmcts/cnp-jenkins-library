
def call(String pluginId, Closure body) {
  if (Jenkins.instance.getPluginManager().getPlugins().find { it.getShortName() == pluginId && it.isActive() } != null) {
    body.call()
  }
}
