
def call(String subscription, Closure block) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    doAcrOrKubectlTask(subscription, false, block)
  } else {
    withDocker('hmcts/cnp-aks-client:az-2.39.0-kubectl-1.20.5-helm-3.5.3', null) {
      doAcrOrKubectlTask(subscription, true, block)
    }
  }
}

def doAcrOrKubectlTask(String subscription, boolean alwaysLogin, Closure block) {
  withSubscriptionLogin(subscription, alwaysLogin) {
    withRegistrySecrets {
      block.call()
    }
  }
}
