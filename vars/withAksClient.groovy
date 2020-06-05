import uk.gov.hmcts.contino.Environment

def call(String subscription, String environment, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.3.0-kubectl-1.18.0-helm-3.1.2', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        def envName = environment.toUpperCase()
        env.AKS_CLUSTER_NAME = "dev-00-aks"
        env.AKS_RESOURCE_GROUP = "aks-infra-dev-rg"
        block.call()
      }
    }
  }
}


def call(String subscription, Closure block) {
  String environment = new Environment(env).previewName
  call(subscription,environment, block)
}

