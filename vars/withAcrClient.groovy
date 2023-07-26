
def call(String subscription, Closure block) {
    doAcrOrKubectlTask(subscription, false, block)
}

def doAcrOrKubectlTask(String subscription, boolean alwaysLogin, Closure block) {
  withSubscriptionLogin(subscription, alwaysLogin) {
    withRegistrySecrets {
      block.call()
    }
  }
}
