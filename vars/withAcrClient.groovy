def call(String subscription, Closure block) {
//  withDocker('hmcts/cnp-aks-client:az-2.3.0-kubectl-1.18.0-helm-3.1.2', null) {
    doAcrOrKubectlTask(subscription, block)
//  }
}

def doAcrOrKubectlTask(String subscription, Closure block) {
  withSubscriptionLogin(subscription) {
    withRegistrySecrets {
      block.call()
    }
  }
}
