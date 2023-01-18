
def call(String subscription, Closure block) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    doAcrOrKubectlTask(subscription, false, block)
  } else {
    withDocker('hmcts/cnp-aks-client:az-2.44.1-kubectl-1.26.0-helm-3.10.3', null) {
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
