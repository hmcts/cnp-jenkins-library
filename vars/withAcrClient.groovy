def call(String subscription, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.0.61-kubectl-1.13.5-helm-2.14.0', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        block.call()
      }
    }
  }
}


