
def call(String pluginId, Closure body) {
  if (pluginEnabled(pluginId)) {
    body.call()
  }
}

@NonCPS
def pluginEnabled(def pluginId) {
  Jenkins.instance.getPluginManager().getPlugins().find { it.getShortName() == pluginId && it.isEnabled() } != null
}
