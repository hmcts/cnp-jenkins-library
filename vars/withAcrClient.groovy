
def call(String subscription, Closure block) {
  withSubscriptionLogin(subscription, alwaysLogin) {
    withRegistrySecrets {
      block.call()
    }
  }
}
