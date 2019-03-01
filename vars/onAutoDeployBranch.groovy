
def call(Closure block) {
  def envSub = autoDeployEnvironment()
  if (envSub) {
    return block.call(envSub.subscriptionName, envSub.subscriptionName)
  }
}
