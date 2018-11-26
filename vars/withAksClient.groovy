def call(String subscription, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.0.50-kubectl-1.12.2-helm-2.11', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        block.call()
      }
    }
  }
}

