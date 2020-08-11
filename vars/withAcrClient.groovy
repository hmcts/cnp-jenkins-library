
def call(String subscription, Closure block) {
  if (env.IS_DOCKER_BUILD_AGENT && env.IS_DOCKER_BUILD_AGENT.toBoolean()) {
    doAcrOrKubectlTask(subscription, false, block)
  } else {
    withDocker('hmcts/cnp-aks-client:az-2.3.0-kubectl-1.18.0-helm-3.1.2-rsync', null) {
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
