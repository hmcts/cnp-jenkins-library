
def call(String subscription, Closure block) {
  withSubscriptionLogin(subscription) {
    withRegistrySecrets {
      block.call()
    }
  }
}
