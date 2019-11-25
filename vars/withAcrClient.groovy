def call(String subscription, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.0.61-kubectl-1.16.2-helm-2.16.1', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        block.call()
      }
    }
  }
}


