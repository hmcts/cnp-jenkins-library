
def call(String pluginId, Closure body) {
  if (Jenkins.instance.getPluginManager().getPlugins().find { it.getShortName() == pluginId && it.isEnabled() } != null) {
    body.call()
  }
}
