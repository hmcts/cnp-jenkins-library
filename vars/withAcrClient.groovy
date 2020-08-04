
def call(String subscription, String product, Closure block) {
  if (env.IS_DOCKER_BUILD_AGENT) {
    doAcrOrKubectlTask(subscription, block)
  } else {
    withDocker('hmcts/cnp-aks-client:az-2.3.0-kubectl-1.18.0-helm-3.1.2-rsync', null) {
      doAcrOrKubectlTask(subscription, block)
    }
  }
}

def doAcrOrKubectlTask(String subscription, Closure block) {
  withSubscriptionLogin(subscription) {
    withRegistrySecrets {
      block.call()
    }
  }
}
