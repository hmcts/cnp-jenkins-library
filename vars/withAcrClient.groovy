
def call(String subscription, Closure block) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    doAcrOrKubectlTask(subscription, false, block)
  } else {
      doAcrOrKubectlTask(subscription, true, block)
  }
}

def doAcrOrKubectlTask(String subscription, boolean alwaysLogin, Closure block) {
  withSubscriptionLogin(subscription, alwaysLogin) {
    withRegistrySecrets {
      block.call()
    }
  }
}
